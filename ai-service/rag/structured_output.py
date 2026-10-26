"""
结构化输出工具 — Pydantic Schema + OpenAI response_format 强制约束

设计模式：Template Method — 统一的调用模板，不同的 Schema 作为变体
"""
import json
import logging
from typing import Type, TypeVar
from pydantic import BaseModel

logger = logging.getLogger(__name__)
T = TypeVar("T", bound=BaseModel)


def structured_llm_call(
    prompt: str,
    schema: Type[T],
    temperature: float = 0.3,
    max_retries: int = 2
) -> T:
    """带 Pydantic Schema 约束的结构化 LLM 调用（全部走 ModelRouter 降级链）"""
    from agents.model_router import get_model_router
    router = get_model_router()

    for attempt in range(max_retries + 1):
        try:
            result = router.call_with_fallback(
                task_type="default",
                messages=[
                    {"role": "system", "content": f"返回符合以下 JSON Schema 的 JSON：{json.dumps(schema.model_json_schema(), ensure_ascii=False)}"},
                    {"role": "user", "content": prompt}
                ],
                temperature=temperature,
                response_format={"type": "json_object"}
            )
            data = json.loads(result["response"].choices[0].message.content)
            return schema.model_validate(data)
        except Exception as e:
            logger.warning(f"Structured output attempt {attempt + 1} failed: {e}")
            if attempt == max_retries:
                return _fallback_parse(router, prompt, schema, temperature)
    return schema()  # 最终降级：返回空 Schema


def _fallback_parse(router, prompt: str, schema: Type[T], temperature: float) -> T:
    """降级：普通 LLM 调用 + 手动 JSON 解析"""
    try:
        result = router.call_with_fallback(
            task_type="default",
            messages=[{"role": "user", "content": prompt + "\n\n只返回 JSON，不要其他内容。"}],
            temperature=temperature
        )
        text = result["response"].choices[0].message.content
        if "```json" in text:
            text = text.split("```json")[1].split("```")[0]
        elif "```" in text:
            text = text.split("```")[1].split("```")[0]
        data = json.loads(text.strip())
        return schema.model_validate(data)
    except Exception as e:
        logger.warning(f"Fallback parse also failed: {e}")
        return schema()


# ========== Pydantic Schema 定义 ==========

class SelfCheckResult(BaseModel):
    passed: bool = True
    confidence: float = 0.5
    reason: str = ""

class ExperienceSuggestion(BaseModel):
    experience_summary: str = ""
    suggestions: list = []
    risk_warnings: list = []

class ActivityPlan(BaseModel):
    timeline: list = []
    budget_reference: float = 0.0
    venue_suggestion: str = ""
    risk_warnings: list = []

class NotificationSummary(BaseModel):
    summary: str = ""
    action_items: list = []

class RegistrationReview(BaseModel):
    ranked_users: list = []
    summary: str = ""


# ========== 新增 Agent Schema ==========

class ActivityContent(BaseModel):
    """活动文案生成 Agent — 结构化输出"""
    title_options: list = []        # 3个备选标题
    description: str = ""           # 活动详情描述
    promotional_copy: str = ""      # 宣传文案（朋友圈/公告用）
    budget_suggestion: str = ""     # 预算建议
    hashtags: list = []             # 宣传标签


class DataInsight(BaseModel):
    """数据洞察 Agent — 结构化输出"""
    summary: str = ""               # 整体分析摘要
    trends: list = []               # 趋势发现
    recommendations: list = []      # 改进建议
    highlight: str = ""             # 亮点表现
    concern: str = ""               # 需关注的问题


class SentimentReport(BaseModel):
    """反馈情感分析 Agent — 结构化输出"""
    overall_sentiment: str = "neutral"  # positive / neutral / negative
    positive_ratio: float = 0.0         # 正面反馈占比
    themes: list = []                   # 主题聚类 [{"theme": "...", "count": n, "sentiment": "..."}]
    key_praises: list = []              # 关键好评
    key_complaints: list = []           # 关键吐槽
    actionable_suggestions: list = []   # 可操作的改进建议
