import logging
import json
from typing import TypedDict, Optional, Dict, Any
from fastapi import APIRouter, HTTPException
from models.schemas import ReportGenerateRequest, ReportResponse
from tools.db_reader import DBReader
from rag.generator import generate_report_content
from rag.knowledge_base import add_document

try:
    from langgraph.graph import StateGraph, START, END
    from langgraph.checkpoint.memory import MemorySaver
    from langgraph.types import interrupt, Command
    _HAS_LANGGRAPH_REPORT = True
except Exception:
    _HAS_LANGGRAPH_REPORT = False

logger = logging.getLogger(__name__)
router = APIRouter()


class ReportState(TypedDict, total=False):
    activity_id: int
    title: str
    draft_content: str
    approved: bool
    feedback: str
    final_content: str
    status: str
    extra_context: Dict[str, Any]


def _build_report_graph():
    graph = StateGraph(ReportState)

    def draft_node(state: ReportState) -> Dict[str, Any]:
        with DBReader() as reader:
            compilation = reader.get_compilation_by_activity(state["activity_id"])
            if not compilation:
                raise HTTPException(status_code=400, detail="活动尚未生成汇编文档，请先等待打卡截止并自动生成汇编")

            activity = reader.get_activity(state["activity_id"])
            if not activity:
                raise HTTPException(status_code=404, detail="活动不存在")

        extra_context = dict(state.get("extra_context") or {})
        extra_context["feedback"] = state.get("feedback") or ""

        content = generate_report_content(
            compilation["content"],
            {
                "title": activity.get("title", ""),
                "start_time": str(activity.get("start_time", "")),
                "end_time": str(activity.get("end_time", "")),
                "location": activity.get("location", ""),
                "current_participants": activity.get("current_participants", 0),
                "budget": float(activity.get("budget", 0))
            },
            extra_context
        )

        return {
            "title": (activity.get("title", "") or "") + "-成果报告",
            "draft_content": content,
            "status": "PENDING_REVIEW"
        }

    def human_review_node(state: ReportState) -> Dict[str, Any]:
        decision = interrupt({
            "message": "请审核 AI 生成的活动报告草稿",
            "title": state.get("title", ""),
            "content_preview": (state.get("draft_content", "") or "")[:1500]
        })

        approved = bool(decision.get("approved", False)) if isinstance(decision, dict) else False
        feedback = (decision.get("feedback") or "") if isinstance(decision, dict) else ""
        return {"approved": approved, "feedback": feedback}

    def finalize_node(state: ReportState) -> Dict[str, Any]:
        if not state.get("approved"):
            return {"final_content": "", "status": "REJECTED"}

        return {"final_content": state.get("draft_content", ""), "status": "CONFIRMED"}

    graph.add_node("draft", draft_node)
    graph.add_node("human_review", human_review_node)
    graph.add_node("finalize", finalize_node)

    graph.add_edge(START, "draft")
    graph.add_edge("draft", "human_review")
    graph.add_edge("human_review", "finalize")
    graph.add_edge("finalize", END)

    return graph.compile(checkpointer=MemorySaver())


_report_graph = None


def _get_report_graph():
    global _report_graph
    if _report_graph is None:
        _report_graph = _build_report_graph()
    return _report_graph


@router.post("/generate")
async def generate_report(request: ReportGenerateRequest):
    try:
        if not _HAS_LANGGRAPH_REPORT:
            raise HTTPException(status_code=500, detail="当前环境未启用 LangGraph 报告智能体")

        graph = _get_report_graph()
        config = {"configurable": {"thread_id": f"report:{request.activity_id}"}}

        state = graph.invoke({"activity_id": request.activity_id, "feedback": request.feedback}, config=config)

        if state.get("status") != "PENDING_REVIEW":
            raise HTTPException(status_code=500, detail="报告生成流程异常")

        return {
            "activity_id": request.activity_id,
            "title": state.get("title", ""),
            "content": state.get("draft_content", ""),
            "status": "PENDING_REVIEW"
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Generate report error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.get("/{activity_id}")
async def get_report(activity_id: int):
    try:
        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    SELECT * FROM ai_activity_report
                    WHERE activity_id = %s AND deleted = 0
                """, (activity_id,))
                report = cursor.fetchone()
                if not report:
                    raise HTTPException(status_code=404, detail="报告不存在")
                return {
                    "id": report["id"],
                    "activity_id": report["activity_id"],
                    "title": report["title"],
                    "content": report["content"],
                    "status": report["status"],
                    "avg_rating": float(report["avg_rating"]) if report["avg_rating"] else None,
                    "participant_count": report["participant_count"],
                    "checkin_count": report["checkin_count"],
                    "revision_count": report["revision_count"]
                }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Get report error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.post("/{activity_id}/confirm")
async def confirm_report(activity_id: int):
    try:
        if not _HAS_LANGGRAPH_REPORT:
            raise HTTPException(status_code=500, detail="当前环境未启用 LangGraph 报告智能体")

        graph = _get_report_graph()
        config = {"configurable": {"thread_id": f"report:{activity_id}"}}

        try:
            state = graph.invoke(Command(resume={"approved": True}), config=config)
        except Exception as e:
            logger.error(f"Report graph resume error: {e}")
            raise HTTPException(status_code=400, detail="恢复报告审核流程失败，请重新生成报告")

        final_content = state.get("final_content") or ""
        title = state.get("title", "")

        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_activity_report (activity_id, title, content, status)
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), status=VALUES(status)
                """, (activity_id, title, final_content, "CONFIRMED"))
                reader.conn.commit()

        return {"status": "CONFIRMED"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Confirm report error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")

@router.post("/{activity_id}/reject")
async def reject_report(activity_id: int, request: dict):
    try:
        if not _HAS_LANGGRAPH_REPORT:
            raise HTTPException(status_code=500, detail="当前环境未启用 LangGraph 报告智能体")

        graph = _get_report_graph()
        config = {"configurable": {"thread_id": f"report:{activity_id}"}}

        try:
            state = graph.invoke(Command(resume={"approved": False, "feedback": request.get("reason", "")}), config=config)
        except Exception as e:
            logger.error(f"Report graph reject error: {e}")
            raise HTTPException(status_code=400, detail="恢复报告审核流程失败，请重新生成报告")

        with DBReader() as reader:
            with reader.conn.cursor() as cursor:
                cursor.execute("""
                    INSERT INTO ai_activity_report (activity_id, title, content, status, reject_reason)
                    VALUES (%s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), status=VALUES(status), reject_reason=VALUES(reject_reason)
                """, (activity_id, state.get("title", ""), "", "REJECTED", request.get("reason", "")))
                reader.conn.commit()

        return {"status": "REJECTED"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Reject report error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")
