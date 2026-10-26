"""
反馈情感分析 Agent — Sentiment Analysis Agent

面试话术：
- Agent 分析打卡记录中的心得、问题、建议
- 自动分类情感倾向（正面/中性/负面）
- 提取主题聚类 + 可操作建议，辅助社团改进
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
from rag.structured_output import structured_llm_call, SentimentReport
from tools.db_reader import DBReader

logger = logging.getLogger(__name__)
router = APIRouter()


class SentimentRequest(BaseModel):
    """情感分析请求"""
    activity_id: Optional[int] = None   # 指定活动ID
    club_id: Optional[int] = None       # 或分析社团所有活动
    limit: int = 50                     # 最多分析的反馈条数


@router.post("/analyze")
async def analyze_sentiment(request: SentimentRequest):
    """
    反馈情感分析 Agent

    输入：活动ID 或 社团ID
    输出：情感分布 + 主题聚类 + 关键好评/吐槽 + 改进建议

    面试亮点：
    - NLP 情感分析 + 主题聚类
    - 结构化输出（SentimentReport Schema）
    - 将非结构化反馈转化为可操作的洞察
    """
    try:
        if not request.activity_id and not request.club_id:
            raise HTTPException(status_code=400, detail="请提供 activity_id 或 club_id")

        reader = DBReader()
        try:
            # 收集打卡反馈
            if request.activity_id:
                with reader.conn.cursor() as cursor:
                    cursor.execute("""
                        SELECT cr.id, cr.rating, cr.reflection, cr.problem, cr.suggestion,
                               u.real_name
                        FROM act_checkin_record cr
                        JOIN sys_user u ON cr.user_id = u.id
                        WHERE cr.activity_id = %s AND cr.deleted = 0
                        ORDER BY cr.created_at DESC
                        LIMIT %s
                    """, (request.activity_id, request.limit))
                    records = cursor.fetchall()
                scope_desc = f"活动 {request.activity_id}"
            else:
                with reader.conn.cursor() as cursor:
                    cursor.execute("""
                        SELECT cr.id, cr.rating, cr.reflection, cr.problem, cr.suggestion,
                               u.real_name, a.title as activity_title
                        FROM act_checkin_record cr
                        JOIN sys_user u ON cr.user_id = u.id
                        JOIN act_activity a ON cr.activity_id = a.id
                        WHERE a.club_id = %s AND cr.deleted = 0 AND a.deleted = 0
                        ORDER BY cr.created_at DESC
                        LIMIT %s
                    """, (request.club_id, request.limit))
                    records = cursor.fetchall()
                scope_desc = f"社团 {request.club_id}"

            if not records:
                return {
                    "overall_sentiment": "neutral",
                    "positive_ratio": 0.0,
                    "themes": [],
                    "key_praises": [],
                    "key_complaints": [],
                    "actionable_suggestions": [],
                    "message": "暂无打卡反馈数据"
                }

            # 构建反馈文本
            feedback_text = f"以下是 {scope_desc} 的 {len(records)} 条成员反馈：\n\n"
            positive_count = 0

            for i, r in enumerate(records, 1):
                rating = r.get("rating", 0) or 0
                reflection = r.get("reflection", "") or ""
                problem = r.get("problem", "") or ""
                suggestion = r.get("suggestion", "") or ""
                name = r.get("real_name", "匿名")
                act_title = r.get("activity_title", "")

                if rating >= 4:
                    positive_count += 1

                feedback_text += f"反馈{i}（{name}"
                if act_title:
                    feedback_text += f", {act_title}"
                feedback_text += f", 评分{rating}/5）：\n"
                if reflection:
                    feedback_text += f"  心得：{reflection[:150]}\n"
                if problem:
                    feedback_text += f"  问题：{problem[:150]}\n"
                if suggestion:
                    feedback_text += f"  建议：{suggestion[:150]}\n"
                feedback_text += "\n"

            positive_ratio = positive_count / max(len(records), 1)

            prompt = f"""你是一位用户反馈分析专家。请分析以下成员打卡反馈，生成情感分析报告。

{feedback_text}

请返回：
- overall_sentiment：整体情感倾向（"positive" / "neutral" / "negative"）
- positive_ratio：正面反馈占比（0.0-1.0，参考值约 {positive_ratio:.2f}）
- themes：主题聚类列表，每项包含 theme（主题名）、count（提及次数）、sentiment（该主题情感）
- key_praises：2-3条关键好评摘要
- key_complaints：2-3条关键吐槽摘要
- actionable_suggestions：2-3条可操作的改进建议"""

            result = structured_llm_call(prompt, SentimentReport, temperature=0.2)

            return {
                "scope": scope_desc,
                "feedback_count": len(records),
                "overall_sentiment": result.overall_sentiment,
                "positive_ratio": result.positive_ratio,
                "themes": result.themes,
                "key_praises": result.key_praises,
                "key_complaints": result.key_complaints,
                "actionable_suggestions": result.actionable_suggestions,
                "raw_positive_ratio": round(positive_ratio, 2)
            }
        finally:
            reader.close()
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Sentiment analysis error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
