import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from models.schemas import ExperienceRequest
from rag.retrieval import hybrid_search
from rag.generator import generate_experience_suggestions
from rag.knowledge_base import get_collection_name
from rag.structured_output import structured_llm_call, ActivityPlan

logger = logging.getLogger(__name__)
router = APIRouter()

@router.post("")
async def get_experience(request: ExperienceRequest):
    try:
        collection = get_collection_name(request.club_id)
        query = f"{request.title} {request.activity_type} 经验 建议 问题"
        results = hybrid_search(collection, query, where={"doc_type": {"$in": ["activity_report", "checkin_compilation"]}})

        if not results:
            # 无历史数据，返回通用模板
            return {
                "has_history": False,
                "experience_summary": "该社团暂无历史活动数据，以下为通用策划建议。",
                "suggestions": [
                    "提前两周发布活动通知，确保足够的报名时间",
                    "活动前确认场地预约成功",
                    "准备备用方案应对突发情况",
                    "安排专人拍照记录活动过程",
                    "活动结束后及时收集成员反馈"
                ],
                "risk_warnings": [
                    "注意控制预算，预留10%-15%的缓冲",
                    "关注天气预报，户外活动需准备应急预案"
                ]
            }

        # 有历史数据，生成建议
        activity_info = {
            "activity_type": request.activity_type,
            "title": request.title,
            "location": request.location or "",
            "max_participants": request.max_participants or 0,
            "budget": request.budget or 0
        }
        result = generate_experience_suggestions(activity_info, results)

        return {
            "has_history": True,
            "experience_summary": result.get("experience_summary", ""),
            "suggestions": result.get("suggestions", []),
            "risk_warnings": result.get("risk_warnings", [])
        }
    except Exception as e:
        logger.error(f"Experience error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")


class ActivitySuggestRequest(BaseModel):
    """活动策划建议请求"""
    club_id: int
    activity_type: str
    title: str
    location: str = ""
    max_participants: int = 0
    budget: float = 0


@router.post("/suggest")
async def suggest_activity_plan(request: ActivitySuggestRequest):
    """
    活动策划助手：基于历史经验生成时间表 + 预算参考 + 风险提醒

    面试亮点：
    - 结构化输出（ActivityPlan Schema）
    - 复用 RAG 检索历史经验 + LLM 生成结构化方案
    """
    try:
        collection = get_collection_name(request.club_id)
        query = f"{request.title} {request.activity_type} 策划 时间 预算"
        results = hybrid_search(collection, query)

        history_text = ""
        if results:
            for i, r in enumerate(results[:3]):
                history_text += f"[参考{i+1}] {r.get('metadata', {}).get('title', '')}: {r['content'][:200]}\n"

        prompt = f"""基于以下历史活动经验，为新活动生成策划方案。

新活动信息：
- 类型：{request.activity_type}
- 标题：{request.title}
- 地点：{request.location}
- 预计人数：{request.max_participants}
- 预算：{request.budget}

历史经验参考：
{history_text if history_text else "暂无历史经验数据。"}

请返回策划方案，包含：
- timeline：时间表列表（每项为一个时间节点和任务描述）
- budget_reference：参考预算金额
- venue_suggestion：场地建议
- risk_warnings：风险提醒列表"""

        result = structured_llm_call(prompt, ActivityPlan, temperature=0.3)

        return {
            "timeline": result.timeline,
            "budget_reference": result.budget_reference,
            "venue_suggestion": result.venue_suggestion,
            "risk_warnings": result.risk_warnings,
            "has_history": len(results) > 0
        }
    except Exception as e:
        logger.error(f"Activity suggest error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
