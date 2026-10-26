"""
活动文案生成 Agent — Content Generation Agent

面试话术：
- Agent 根据活动类型/主题自动生成标题、详情、宣传文案、预算建议
- 结构化输出（ActivityPlan Schema）保证 LLM 返回格式
- 复用 RAG 检索历史活动经验作为生成参考
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
from rag.structured_output import structured_llm_call, ActivityContent
from rag.retrieval import hybrid_search
from rag.knowledge_base import get_collection_name

logger = logging.getLogger(__name__)
router = APIRouter()


class ContentGenerateRequest(BaseModel):
    """活动文案生成请求"""
    club_id: int
    activity_type: str               # workshop / competition / lecture / social / volunteer
    topic: str                       # 活动主题
    target_audience: str = "全体同学"  # 目标受众
    location: Optional[str] = ""
    estimated_participants: int = 0
    budget: float = 0


@router.post("/generate")
async def generate_content(request: ContentGenerateRequest):
    """
    活动文案生成 Agent

    输入：活动类型、主题、目标受众、预算等
    输出：3个备选标题 + 活动详情 + 宣传文案 + 预算建议 + 标签

    面试亮点：
    - 结构化输出强制约束 LLM 返回格式（ActivityContent Schema）
    - 复用 RAG 检索历史同类活动经验作为生成参考
    - 一次调用生成完整文案包，减少用户操作
    """
    try:
        # 检索历史同类活动经验
        collection = get_collection_name(request.club_id)
        query = f"{request.activity_type} {request.topic} 活动 经验"
        history_results = hybrid_search(collection, query, top_k=3)

        history_text = ""
        if history_results:
            for i, r in enumerate(history_results):
                title = r.get("metadata", {}).get("title", "")
                history_text += f"[参考{i+1}] {title}: {r['content'][:150]}\n"

        prompt = f"""你是一个社团活动策划专家。请为以下活动生成完整的文案包。

活动信息：
- 类型：{request.activity_type}
- 主题：{request.topic}
- 目标受众：{request.target_audience}
- 地点：{request.location or "待定"}
- 预计人数：{request.estimated_participants}
- 预算：{request.budget}元

{"历史同类活动经验：\n" + history_text if history_text else ""}

请返回：
- title_options：3个备选标题（有吸引力，适合宣传）
- description：活动详情描述（200字左右，包含活动目的、内容、收获）
- promotional_copy：朋友圈/公告宣传文案（100字以内，有感染力）
- budget_suggestion：预算分配建议
- hashtags：5个宣传标签（如 #编程 #Workshop）"""

        result = structured_llm_call(prompt, ActivityContent, temperature=0.7)

        return {
            "title_options": result.title_options,
            "description": result.description,
            "promotional_copy": result.promotional_copy,
            "budget_suggestion": result.budget_suggestion,
            "hashtags": result.hashtags,
            "has_history_reference": len(history_results) > 0
        }
    except Exception as e:
        logger.error(f"Content generation error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
