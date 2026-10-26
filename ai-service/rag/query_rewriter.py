"""
LLM 驱动的查询改写

设计模式：Strategy 模式 — 改写策略可替换（LLM / 规则引擎 / 微调模型）
"""
import json
import logging
from typing import List, Dict

logger = logging.getLogger(__name__)


def rewrite_query(original_query: str, chat_history: List[Dict]) -> str:
    """
    LLM 驱动的智能查询改写

    功能：
    - 消除指代（“它”、“这个”等替换为具体名词）
    - 去除口语化表达，保留核心关键词
    - 如果问题包含多个意图，保留最主要的一个

    降级：改写失败时返回原始查询
    """
    try:
        from rag.generator import _router_call

        history_text = ""
        if chat_history:
            recent = chat_history[-4:]
            history_text = json.dumps(recent, ensure_ascii=False)

        prompt = f"""你是一个查询改写助手。根据对话历史和用户最新问题，生成一个适合知识库检索的查询语句。

规则：
1. 消除指代（“它”、“这个”等替换为具体名词）
2. 去除口语化表达，保留核心关键词
3. 如果问题包含多个意图，保留最主要的一个
4. 直接输出改写后的查询语句，不要其他内容

对话历史：
{history_text}

用户最新问题：{original_query}

改写后的查询："""

        rewritten = _router_call(
            "lightweight",
            [{"role": "user", "content": prompt}],
            temperature=0.1,
            max_tokens=100
        ).strip()

        if rewritten and rewritten != original_query:
            logger.info(f"Query rewritten: '{original_query}' → '{rewritten}'")
            return rewritten

        return original_query
    except Exception as e:
        logger.warning(f"Query rewrite failed: {e}")
        return original_query  # 降级：返回原始查询


def hyde_rewrite(original_query: str, chat_history: List[Dict]) -> str:
    """
    HyDE（Hypothetical Document Embeddings）策略：
    让 LLM 生成一个假设性回答，再用该回答去检索，以提升语义匹配。
    """
    try:
        from rag.generator import _router_call

        history_text = ""
        if chat_history:
            recent = chat_history[-4:]
            history_text = json.dumps(recent, ensure_ascii=False)

        prompt = f"""请根据以下问题，生成一段可能出现在知识库中的假设性回答（80-150字），用于语义检索。
不要解释，直接输出假设性回答。

对话历史：
{history_text}

问题：{original_query}
假设性回答："""

        hypothetical = _router_call(
            "lightweight",
            [{"role": "user", "content": prompt}],
            temperature=0.2,
            max_tokens=220
        ).strip()

        if hypothetical:
            logger.info(f"HyDE rewrite: '{original_query}' → '{hypothetical[:40]}...'")
            return hypothetical

        return original_query
    except Exception as e:
        logger.warning(f"HyDE rewrite failed: {e}")
        return original_query
