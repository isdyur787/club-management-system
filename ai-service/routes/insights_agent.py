"""
数据洞察 Agent — Data Insights Agent

面试话术：
- Agent 读取社团年度统计数据，自动生成分析报告
- 包含趋势发现、亮点识别、问题预警、改进建议
- Text-to-Insight 模式：将结构化数据转化为自然语言洞察
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional, Dict, Any
from rag.structured_output import structured_llm_call, DataInsight
from tools.db_reader import DBReader

try:
    from langgraph.graph import StateGraph, START, END
    from langgraph.checkpoint.memory import MemorySaver
    from langgraph.types import interrupt, Command
    from typing import TypedDict
    _HAS_LANGGRAPH_INSIGHT = True
except Exception:
    _HAS_LANGGRAPH_INSIGHT = False

logger = logging.getLogger(__name__)
router = APIRouter()


class InsightRequest(BaseModel):
    """数据洞察请求"""
    club_id: int
    year: Optional[int] = None  # None = 当前年


class InsightState(TypedDict, total=False):
    club_id: int
    year: int
    data_summary: str
    draft: Dict[str, Any]
    approved: bool
    feedback: str
    final: Dict[str, Any]


def _build_insight_graph():
    graph = StateGraph(InsightState)

    def collect_data_node(state: InsightState) -> Dict[str, Any]:
        import datetime
        reader = DBReader()
        try:
            year = state.get("year") or datetime.datetime.now().year
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    SELECT id, title, type, status, start_time, 
                           max_participants, current_participants, budget
                    FROM act_activity
                    WHERE club_id = %s AND deleted = 0
                      AND YEAR(start_time) = %s
                    ORDER BY start_time
                """, (state["club_id"], year))
                activities = cursor.fetchall()

            if not activities:
                return {
                    "year": year,
                    "data_summary": "",
                    "draft": {
                        "summary": f"该社团在 {year} 年暂无活动记录。",
                        "trends": [],
                        "recommendations": ["建议增加活动频率，提升社团活跃度"],
                        "highlight": "",
                        "concern": "缺乏活动数据，无法生成有效分析"
                    }
                }

            total_activities = len(activities)
            total_participants = sum(a.get("current_participants", 0) or 0 for a in activities)
            types = {}
            monthly = {}
            completed = 0

            for a in activities:
                act_type = a.get("type", "other") or "other"
                types[act_type] = types.get(act_type, 0) + 1

                start = a.get("start_time")
                if start:
                    month = start.month if hasattr(start, 'month') else 1
                    monthly[month] = monthly.get(month, 0) + 1

                if a.get("status") == "COMPLETED":
                    completed += 1

            avg_participants = total_participants / max(total_activities, 1)
            completion_rate = completed / max(total_activities, 1) * 100

            data_summary = f"""社团 {state['club_id']} 在 {year} 年的活动数据：
- 活动总数：{total_activities}
- 已完成：{completed}（完成率 {completion_rate:.0f}%）
- 总参与人次：{total_participants}
- 平均每次活动参与：{avg_participants:.1f} 人
- 活动类型分布：{types}
- 月度分布：{dict(sorted(monthly.items()))}
- 活动列表：
"""
            for a in activities[:15]:
                title = a.get("title", "")
                participants = a.get("current_participants", 0) or 0
                max_p = a.get("max_participants", 0) or 0
                status = a.get("status", "")
                act_type = a.get("type", "")
                data_summary += f"  - {title} ({act_type}, {participants}/{max_p}人, {status})\n"

            return {"year": year, "data_summary": data_summary}
        finally:
            reader.close()

    def draft_node(state: InsightState) -> Dict[str, Any]:
        data_summary = state.get("data_summary", "")
        if not data_summary:
            return {"draft": state.get("draft", {})}

        prompt = f"""你是一位数据分析专家。请基于以下社团年度活动数据，生成一份简洁的分析报告。

{data_summary}

请返回：
- summary：整体分析摘要（100字以内）
- trends：2-3个趋势发现
- recommendations：2-3条改进建议
- highlight：最突出的亮点表现
- concern：最需要关注的问题"""

        result = structured_llm_call(prompt, DataInsight, temperature=0.3)
        return {
            "draft": {
                "summary": result.summary,
                "trends": result.trends,
                "recommendations": result.recommendations,
                "highlight": result.highlight,
                "concern": result.concern
            }
        }

    def human_review_node(state: InsightState) -> Dict[str, Any]:
        decision = interrupt({
            "message": "请确认 AI 生成的数据洞察是否可发布",
            "draft": state.get("draft", {})
        })

        approved = bool(decision.get("approved", False)) if isinstance(decision, dict) else False
        feedback = (decision.get("feedback") or "") if isinstance(decision, dict) else ""
        return {"approved": approved, "feedback": feedback}

    def finalize_node(state: InsightState) -> Dict[str, Any]:
        draft = dict(state.get("draft") or {})
        if state.get("feedback"):
            draft["feedback"] = state["feedback"]

        if not state.get("approved"):
            return {"final": {"status": "REJECTED", **draft}}

        return {"final": {"status": "CONFIRMED", **draft}}

    graph.add_node("collect_data", collect_data_node)
    graph.add_node("draft", draft_node)
    graph.add_node("human_review", human_review_node)
    graph.add_node("finalize", finalize_node)

    graph.add_edge(START, "collect_data")
    graph.add_edge("collect_data", "draft")
    graph.add_edge("draft", "human_review")
    graph.add_edge("human_review", "finalize")
    graph.add_edge("finalize", END)

    return graph.compile(checkpointer=MemorySaver())


_insight_graph = None


def _get_insight_graph():
    global _insight_graph
    if _insight_graph is None:
        _insight_graph = _build_insight_graph()
    return _insight_graph


@router.post("/generate")
async def generate_insight(request: InsightRequest):
    try:
        if not _HAS_LANGGRAPH_INSIGHT:
            raise HTTPException(status_code=500, detail="当前环境未启用 LangGraph 数据洞察智能体")

        graph = _get_insight_graph()
        config = {"configurable": {"thread_id": f"insight:{request.club_id}:{request.year or 'current'}"}}

        state = graph.invoke({"club_id": request.club_id, "year": request.year, "final": {}}, config=config)
        final = state.get("final") or {"status": "PENDING_REVIEW", **(state.get("draft") or {})}
        data_points = {}
        if state.get("year") and state.get("draft"):
            summary = (state.get("draft") or {}).get("summary", "")
            if summary:
                data_points["year"] = state["year"]

        if final.get("status") == "REJECTED":
            return {
                "club_id": request.club_id,
                "year": state.get("year"),
                "status": "REJECTED",
                "summary": final.get("summary", ""),
                "trends": final.get("trends", []),
                "recommendations": final.get("recommendations", []),
                "highlight": final.get("highlight", ""),
                "concern": final.get("concern", ""),
                "feedback": final.get("feedback", "")
            }

        return {
            "club_id": request.club_id,
            "year": state.get("year"),
            "status": final.get("status", "PENDING_REVIEW"),
            "summary": final.get("summary", ""),
            "trends": final.get("trends", []),
            "recommendations": final.get("recommendations", []),
            "highlight": final.get("highlight", ""),
            "concern": final.get("concern", ""),
            "data_points": data_points
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Insight generation error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")


class InsightReviewRequest(InsightRequest):
    approved: bool
    feedback: str = ""


@router.post("/review")
async def review_insight(request: InsightReviewRequest):
    graph = _get_insight_graph()
    config = {"configurable": {"thread_id": f"insight:{request.club_id}:{request.year or 'current'}"}}
    if not graph.get_state(config).next:
        raise HTTPException(status_code=400, detail="没有待审核的数据洞察，请先生成")
    state = graph.invoke(Command(resume={"approved": request.approved, "feedback": request.feedback}), config=config)
    return {"club_id": request.club_id, "year": state.get("year"), **state.get("final", {})}
