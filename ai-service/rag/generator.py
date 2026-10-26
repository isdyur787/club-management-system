import json
import logging
from typing import List, Dict, Optional
from config import LLM_TEMPERATURE

logger = logging.getLogger(__name__)


def _router_call(task_type: str, messages: List[dict],
                 temperature: float = None, max_tokens: int = None) -> str:
    """
    统一 LLM 调用入口 — 全部走 ModelRouter 降级链

    设计模式：Facade — 屏蔽 ModelRouter 调用细节，提供简洁接口
    """
    from agents.model_router import get_model_router
    router = get_model_router()
    result = router.call_with_fallback(
        task_type=task_type,
        messages=messages,
        temperature=temperature if temperature is not None else LLM_TEMPERATURE,
        max_tokens=max_tokens
    )
    return result["response"].choices[0].message.content


def get_context_for_generation(context_docs: List[Dict], max_chars: int = 3000) -> str:
    """将检索文档拼接为上下文文本（Facade 模式，统一上下文构建逻辑）"""
    if not context_docs:
        return ""
    context_text = ""
    for i, doc in enumerate(context_docs):
        title = doc.get("metadata", {}).get("title", "未知来源")
        chunk = doc["content"][:max_chars // len(context_docs)]
        context_text += f"\n[来源{i+1}] {title}\n{chunk}\n"
    return context_text

def build_rag_messages(query: str, context_docs: List[Dict],
                       chat_history: List[Dict] = None,
                       context_max_chars: int = 3000) -> List[Dict]:
    """
    构建 RAG 对话消息列表（公共 prompt 构建逻辑）。

    将上下文文档、对话历史压缩、系统提示词组装为 OpenAI messages 格式，
    供 generate_answer 和流式生成共用，避免 DRY 违反。

    返回：
        List[Dict] — 可直接传给 LLM 的 messages 列表
    """
    context_text = get_context_for_generation(context_docs, max_chars=context_max_chars)

    # 对话历史压缩
    compressed_history = []
    if chat_history and len(chat_history) > 6:
        summary = summarize_history(chat_history)
        compressed_history = [{"role": "system", "content": f"之前的对话摘要：{summary}"}]
    elif chat_history:
        compressed_history = chat_history[-10:]

    system_prompt = f"""你是一个社团知识助手。基于以下参考资料回答用户的问题。

规则：
1. 只基于提供的参考资料回答，不要编造信息
2. 如果参考资料中没有相关信息，明确告知用户
3. 回答中标注引用来源，格式为 [来源1]、[来源2]

参考资料：
{context_text}"""

    messages = [{"role": "system", "content": system_prompt}]
    messages.extend(compressed_history)
    messages.append({"role": "user", "content": query})
    return messages


def generate_answer(query: str, context_docs: List[Dict],
                    chat_history: List[Dict] = None) -> Dict:
    messages = build_rag_messages(query, context_docs, chat_history)

    answer = _router_call("rag", messages, temperature=LLM_TEMPERATURE)

    sources = []
    for i, doc in enumerate(context_docs):
        sources.append({
            "doc_id": doc.get("id", ""),
            "title": doc.get("metadata", {}).get("title", ""),
            "snippet": doc["content"][:200] + "..." if len(doc["content"]) > 200 else doc["content"],
            "doc_type": doc.get("metadata", {}).get("doc_type", "")
        })

    return {"answer": answer, "sources": sources}

def self_check_answer(answer: str, context_docs: List[Dict]) -> Dict:
    """
    自检：判断回答中的关键事实是否能在检索内容中找到依据
    使用结构化输出（Pydantic Schema）保证返回格式
    
    返回：
        {"passed": bool, "confidence": float, "reason": str}
    """
    from rag.structured_output import structured_llm_call, SelfCheckResult

    context_text = ""
    for i, doc in enumerate(context_docs):
        context_text += f"\n[参考{i+1}] {doc['content'][:300]}\n"
    
    prompt = f"""请判断以下回答是否忠实于参考资料。

判断规则：
1. 回答中的关键事实和数据是否都能在参考资料中找到依据
2. 是否有编造、臆测的内容
3. 是否过度推断

参考资料：
{context_text}

回答：
{answer}

请返回判断结果，包含：passed（是否通过）、confidence（0.0-1.0 置信度）、reason（简要说明）。"""

    try:
        result = structured_llm_call(prompt, SelfCheckResult, temperature=0.1)
        return {"passed": result.passed, "confidence": result.confidence, "reason": result.reason}
    except Exception as e:
        logger.warning(f"Self-check failed, defaulting to passed: {e}")
        return {"passed": True, "confidence": 0.5, "reason": "自检异常，默认通过"}

def generate_fallback_answer(query: str, context_docs: List[Dict]) -> str:
    """
    降级回答：当 LLM 生成失败时，返回检索结果摘要
    """
    if not context_docs:
        return "抱歉，知识库中未找到相关信息，且 AI 生成服务暂不可用。"
    
    summary = "以下为知识库中的相关内容（AI 生成暂不可用，仅供参考）：\n\n"
    for i, doc in enumerate(context_docs[:3]):
        title = doc.get("metadata", {}).get("title", "未知来源")
        snippet = doc["content"][:200]
        summary += f"[{i+1}] {title}：{snippet}...\n\n"
    return summary

def generate_report_content(compilation_content: str, activity_info: Dict,
                            materials: Dict) -> str:
    prompt = f"""基于以下信息生成活动成果报告：

活动信息：
- 标题：{activity_info.get('title', '')}
- 时间：{activity_info.get('start_time', '')} ~ {activity_info.get('end_time', '')}
- 地点：{activity_info.get('location', '')}
- 参与人数：{activity_info.get('current_participants', 0)}
- 预算：{activity_info.get('budget', 0)}

汇编文档内容：
{compilation_content}

审核修改意见（如有）：
{materials.get('feedback') or '无'}

请生成结构化报告，包含：
一、活动基本信息
二、成员反馈汇总（含评分统计、收获摘要、建议归类）
三、经验教训
四、附件清单

使用 Markdown 格式。"""

    content = _router_call("rag", [{"role": "user", "content": prompt}], temperature=0.3)
    return content

def generate_experience_suggestions(activity_info: Dict,
                                     search_results: List[Dict]) -> Dict:
    """使用结构化输出生成经验建议"""
    from rag.structured_output import structured_llm_call, ExperienceSuggestion

    prompt = f"""基于以下历史活动成果，为新活动提供策划建议：

新活动信息：
- 类型：{activity_info.get('activity_type', '')}
- 标题：{activity_info.get('title', '')}
- 地点：{activity_info.get('location', '')}
- 预计人数：{activity_info.get('max_participants', 0)}
- 预算：{activity_info.get('budget', 0)}

历史活动经验：
{json.dumps(search_results, ensure_ascii=False, indent=2)}

请返回策划建议，包含：experience_summary（经验总结）、suggestions（建议列表）、risk_warnings（风险提醒列表）。"""

    try:
        result = structured_llm_call(prompt, ExperienceSuggestion, temperature=0.3)
        return {
            "experience_summary": result.experience_summary,
            "suggestions": result.suggestions,
            "risk_warnings": result.risk_warnings
        }
    except Exception as e:
        logger.warning(f"Experience suggestions failed: {e}")
        return {"experience_summary": "生成建议失败，请参考通用策划模板。", "suggestions": [], "risk_warnings": []}


def summarize_history(chat_history: List[Dict], max_rounds: int = 3) -> str:
    """
    对话历史摘要压缩（Facade 模式）
    当历史超过 max_rounds 轮（2*max_rounds 条消息），用 LLM 压缩为摘要
    """
    if not chat_history or len(chat_history) <= max_rounds * 2:
        return ""

    # 取最近的 max_rounds 轮对话
    recent = chat_history[-(max_rounds * 2):]
    history_text = ""
    for msg in recent:
        role = "用户" if msg.get("role") == "user" else "助手"
        history_text += f"{role}：{msg.get('content', '')[:200]}\n"

    prompt = f"""请将以下对话历史压缩为一段简洁的摘要（不超过100字），保留关键信息和上下文：

{history_text}

请直接返回摘要文本，不要加前缀。"""

    try:
        summary = _router_call("lightweight", [{"role": "user", "content": prompt}],
                               temperature=0.3, max_tokens=200)
        return summary.strip()
    except Exception as e:
        logger.warning(f"History summarization failed: {e}")
        # 降级：拼接最近几条
        return " | ".join(
            msg.get("content", "")[:50] for msg in chat_history[-4:]
        )
