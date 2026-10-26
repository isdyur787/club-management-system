"""
报名审核建议路由

面试话术：
- LLM 基于报名者信息和活动要求生成审核建议
- 结构化输出保证返回格式（Pydantic Schema）
- 辅助人工决策，不自动审批
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from rag.structured_output import structured_llm_call, RegistrationReview

logger = logging.getLogger(__name__)
router = APIRouter()


class ReviewSuggestRequest(BaseModel):
    """报名审核建议请求"""
    activity_id: int
    activity_title: str
    max_participants: int
    current_count: int
    registrations: List[dict]  # [{user_id, username, registered_at, remark}, ...]


@router.post("/review-suggest")
async def review_suggest(request: ReviewSuggestRequest):
    """
    报名审核建议：基于报名者信息生成排序建议和审核参考

    面试亮点：
    - 结构化输出（RegistrationReview Schema）
    - 辅助决策而非自动审批，体现人机协作设计
    """
    try:
        if not request.registrations:
            return {"ranked_users": [], "summary": "暂无报名者。"}

        # 构建报名信息
        reg_text = ""
        for i, r in enumerate(request.registrations):
            reg_text += f"{i+1}. 用户ID={r.get('user_id', '')} 姓名={r.get('username', '')} "
            reg_text += f"报名时间={r.get('registered_at', '')} "
            if r.get('remark'):
                reg_text += f"备注={r.get('remark', '')}"
            reg_text += "\n"

        prompt = f"""基于以下活动信息和报名者列表，生成审核建议。

活动信息：
- 标题：{request.activity_title}
- 最大人数：{request.max_participants}
- 当前已报：{request.current_count}
- 剩余名额：{max(0, request.max_participants - request.current_count)}

报名者列表：
{reg_text}

请返回：
- ranked_users：按建议优先级排序的用户列表，每项包含 user_id 和 reason（推荐理由）
- summary：整体审核建议摘要"""

        result = structured_llm_call(prompt, RegistrationReview, temperature=0.3)

        return {
            "ranked_users": result.ranked_users,
            "summary": result.summary,
            "activity_id": request.activity_id
        }
    except Exception as e:
        logger.error(f"Review suggest error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
