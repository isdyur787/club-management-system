"""
智能通知摘要路由

面试话术：
- 使用 LLM 将批量通知压缩为摘要 + 待办事项
- 结构化输出保证返回格式（Pydantic Schema）
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from rag.structured_output import structured_llm_call, NotificationSummary

logger = logging.getLogger(__name__)
router = APIRouter()


class NotificationSummarizeRequest(BaseModel):
    """通知摘要请求"""
    club_id: int
    notifications: List[dict]  # [{title, content, type, created_at}, ...]
    user_id: Optional[int] = None


@router.post("/summarize")
async def summarize_notifications(request: NotificationSummarizeRequest):
    """
    智能通知摘要：将多条通知压缩为一段摘要 + 待办事项列表

    面试亮点：
    - 结构化输出（Pydantic Schema 约束 LLM 返回格式）
    - 减少用户阅读负担，提取关键行动项
    """
    try:
        if not request.notifications:
            return {"summary": "暂无新通知。", "action_items": []}

        # 构建通知文本
        notif_text = ""
        for i, n in enumerate(request.notifications[:20]):  # 最多处理 20 条
            notif_text += f"{i+1}. [{n.get('type', '通知')}] {n.get('title', '')}：{n.get('content', '')[:150]}\n"

        prompt = f"""请将以下社团通知压缩为一段简洁的摘要（不超过200字），并提取需要用户采取行动的待办事项。

通知列表：
{notif_text}

请返回：
- summary：通知摘要
- action_items：待办事项列表（每项简短描述，如无待办则为空列表）"""

        result = structured_llm_call(prompt, NotificationSummary, temperature=0.3)

        return {
            "summary": result.summary,
            "action_items": result.action_items,
            "total_notifications": len(request.notifications)
        }
    except Exception as e:
        logger.error(f"Notification summarize error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
