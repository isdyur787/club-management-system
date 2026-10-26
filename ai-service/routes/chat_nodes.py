"""
chat_nodes.py — LangGraph 状态图节点定义

包含 RAG 问答状态图的全部节点函数、状态定义、图构建逻辑。
"""

import logging
import threading
from typing import TypedDict, Optional, List, Dict

from routes.chat_deps import _filter_garbled_results
from rag.retrieval import hybrid_search
from rag.generator import generate_answer, generate_fallback_answer, self_check_answer
from rag.reranker import rerank
from rag.knowledge_base import get_collection_name
from rag.query_rewriter import rewrite_query, hyde_rewrite
from config import RERANKER_TOP_K, SELF_CHECK_THRESHOLD, COARSE_TOP_K

logger = logging.getLogger(__name__)


# ========== LangGraph State 定义 ==========

class ChatState(TypedDict):
    """RAG 问答状态图"""
    question: str
    club_id: int
    session_id: str
    history: List[Dict]
    coarse_results: List[Dict]
    fine_results: List[Dict]
    answer: str
    sources: List[Dict]
    check_passed: bool
    check_confidence: float
    retry_count: int
    error: Optional[str]


# ========== 图节点函数 ==========

def retrieve_node(state: ChatState) -> dict:
    """节点1：粗排检索"""
    try:
        collection = get_collection_name(state["club_id"])
        results = hybrid_search(collection, state["question"], top_k=COARSE_TOP_K)
        return {"coarse_results": results or []}
    except Exception as e:
        logger.error(f"Retrieval failed: {e}")
        return {"coarse_results": [], "error": f"检索失败: {e}"}


def rerank_node(state: ChatState) -> dict:
    """节点2：Reranker 精排"""
    candidates = _filter_garbled_results(state["coarse_results"])
    if not candidates:
        return {"fine_results": []}
    try:
        fine = rerank(state["question"], candidates, top_k=RERANKER_TOP_K)
        return {"fine_results": fine}
    except Exception as e:
        logger.warning(f"Rerank failed: {e}")
        return {"fine_results": candidates[:RERANKER_TOP_K]}


def relevance_check_node(state: ChatState) -> dict:
    """节点3：相关性判断"""
    fine_results = state["fine_results"]
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": [], "check_passed": True}
    max_score = max(doc.get("rerank_score", 0) for doc in fine_results)
    if max_score < 1.0 and state["retry_count"] == 0:
        return {"retry_count": 1}
    return {"retry_count": state["retry_count"]}


def rewrite_query_node(state: ChatState) -> dict:
    """节点4：LLM 驱动的查询改写 + HyDE"""
    rewritten = rewrite_query(state["question"], state["history"])
    queries = [state["question"]]
    if rewritten != state["question"]:
        queries.append(rewritten)

    hyde_query = hyde_rewrite(state["question"], state["history"])
    if hyde_query and hyde_query != state["question"] and hyde_query not in queries:
        queries.append(hyde_query)

    try:
        collection = get_collection_name(state["club_id"])
        best_results = state["fine_results"]
        best_max = max((doc.get("rerank_score", 0) for doc in best_results), default=0)

        for q in queries:
            coarse = hybrid_search(collection, q, top_k=COARSE_TOP_K)
            fine = rerank(q, coarse or [], top_k=RERANKER_TOP_K)
            candidate_max = max((doc.get("rerank_score", 0) for doc in fine), default=0)
            if candidate_max > best_max:
                best_results = fine
                best_max = candidate_max

        return {"fine_results": best_results, "retry_count": state["retry_count"] + 1}
    except Exception as e:
        logger.warning(f"Query rewrite failed: {e}")
        return {"retry_count": state["retry_count"] + 1}


def generate_node(state: ChatState) -> dict:
    """节点5：LLM 生成回答"""
    fine_results = _filter_garbled_results(state["fine_results"])
    if not fine_results:
        return {"answer": "抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。",
                "sources": []}
    try:
        gen_result = generate_answer(state["question"], fine_results, state["history"])
        return {"answer": gen_result["answer"], "sources": gen_result["sources"]}
    except Exception as e:
        logger.error(f"Generation failed: {e}")
        fallback = generate_fallback_answer(state["question"], fine_results)
        return {"answer": fallback, "sources": [], "error": str(e)}


def self_check_node(state: ChatState) -> dict:
    """节点6：自检 — 忠实度校验"""
    answer = state.get("answer", "")
    fine_results = state.get("fine_results", [])

    if not fine_results or "AI 生成暂不可用" in answer:
        return {"check_passed": True, "check_confidence": 1.0}

    try:
        result = self_check_answer(answer, fine_results)
        passed = result["passed"] and result["confidence"] >= SELF_CHECK_THRESHOLD

        final_answer = answer
        if not passed:
            final_answer = f"⚠️ 以下回答可能不完全准确，仅供参考：\n\n{answer}"

        return {
            "answer": final_answer,
            "check_passed": passed,
            "check_confidence": result["confidence"]
        }
    except Exception as e:
        logger.warning(f"Self-check node failed: {e}")
        return {"check_passed": True, "check_confidence": 0.5}


# ========== 构建 LangGraph 状态图 ==========

def build_chat_graph():
    """构建 RAG 问答状态图"""
    from langgraph.graph import StateGraph, START, END

    graph = StateGraph(ChatState)

    graph.add_node("retrieve", retrieve_node)
    graph.add_node("rerank", rerank_node)
    graph.add_node("relevance_check", relevance_check_node)
    graph.add_node("rewrite_query", rewrite_query_node)
    graph.add_node("generate", generate_node)
    graph.add_node("self_check", self_check_node)

    graph.add_edge(START, "retrieve")
    graph.add_edge("retrieve", "rerank")
    graph.add_edge("rerank", "relevance_check")

    def should_rewrite(state: ChatState) -> str:
        if state.get("retry_count", 0) == 0 and state.get("fine_results"):
            max_score = max(doc.get("rerank_score", 0) for doc in state["fine_results"])
            if max_score < 1.0:
                return "rewrite"
        return "generate"

    graph.add_conditional_edges(
        "relevance_check",
        should_rewrite,
        {"rewrite": "rewrite_query", "generate": "generate"}
    )

    graph.add_edge("rewrite_query", "generate")
    graph.add_edge("generate", "self_check")
    graph.add_edge("self_check", END)

    return graph.compile()


_chat_graph = None
_chat_graph_lock = threading.Lock()


def get_chat_graph():
    global _chat_graph
    if _chat_graph is None:
        with _chat_graph_lock:
            if _chat_graph is None:  # 双重检查锁定
                _chat_graph = build_chat_graph()
    return _chat_graph
