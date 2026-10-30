# 文件职责：组装问答依赖，提供普通问答、SSE、会话创建及模型状态接口。
"""
chat.py — 路由薄壳

组装 chat_deps / chat_nodes / chat_agent / chat_stream 模块，
注册 FastAPI 路由端点。
"""

import json
import uuid
import asyncio
import datetime
import time
import logging
from fastapi import APIRouter, HTTPException, Request as FastAPIRequest
from fastapi.responses import StreamingResponse
from models.schemas import ChatRequest, ChatResponse, SessionCreateRequest

# 从拆分模块导入共享依赖（main.py 也通过此路径访问）
from routes.chat_deps import (
    redis_client, _executor, _guardrails, _semantic_cache,
    _validate_request_guardrails,
    _validate_fc_tool_arguments,
    load_history, save_history,
)
from routes.chat_nodes import get_chat_graph
from routes.chat_agent import _try_function_calling
from routes.chat_stream import stream_event_generator
from rag.latency import get_llm_limiter

logger = logging.getLogger(__name__)
router = APIRouter()


# ========== API 端点 ==========

@router.post("", response_model=ChatResponse)
async def chat(request: ChatRequest, http_request: FastAPIRequest):
    try:
        # 1. 统一前置校验（安全 + 话题 + 限流）
        is_safe, reason = _validate_request_guardrails(
            request.message,
            request.club_id,
            rate_limit_key=f"chat:{request.club_id}:{request.session_id or 'anonymous'}"
        )
        if not is_safe:
            return ChatResponse(
                session_id=request.session_id or str(uuid.uuid4()),
                answer=reason,
                sources=[]
            )

        session_id = request.session_id or str(uuid.uuid4())
        trace_id = http_request.headers.get("X-Trace-Id")
        trace_id = trace_id or str(uuid.uuid4())

        # 2. 仅无历史的首轮问答复用答案缓存，避免同一提问在不同上下文中错误命中。
        history = load_history(session_id)
        cached = _semantic_cache.get(request.club_id, request.message) if not history else None
        if cached:
            save_history(session_id, request.message, cached.get("answer", ""))
            return ChatResponse(
                session_id=session_id,
                answer=cached.get("answer", ""),
                sources=cached.get("sources", [])
            )

        # 3. Function Calling 尝试
        fc_result = await asyncio.to_thread(_try_function_calling, request.message, request.club_id, history)
        if fc_result:
            # 输出过滤
            fc_result["answer"] = _guardrails.filter_output(fc_result["answer"])
            # 工具回答来自实时查询，这里只保存会话历史，不写入语义答案缓存。
            save_history(session_id, request.message, fc_result["answer"])
            return ChatResponse(
                session_id=session_id,
                answer=fc_result["answer"],
                sources=fc_result["sources"]
            )

        # 4. 获取对话历史
        history_key = f"chat:{session_id}:history"

        # 5. 执行统一 RAG 流程（保留 LangGraph 编排优势）
        graph = get_chat_graph()
        initial_state = {
            "question": request.message,
            "club_id": request.club_id,
            "session_id": session_id,
            "history": history,
            "coarse_results": [],
            "fine_results": [],
            "answer": "",
            "sources": [],
            "check_passed": False,
            "check_confidence": 0.0,
            "retry_count": 0,
            "error": None
        }

        rag_started_at = time.time()
        try:
            loop = asyncio.get_running_loop()
            limiter = get_llm_limiter()
            await limiter.acquire()
            try:
                final_state = await asyncio.wait_for(
                    loop.run_in_executor(_executor, graph.invoke, initial_state),
                    timeout=90.0
                )
            finally:
                limiter.release()
        except asyncio.TimeoutError:
            logger.error(f"RAG pipeline timed out for session={session_id} trace={trace_id}")
            return ChatResponse(session_id=session_id, answer="抱歉，处理超时，请稍后重试。", sources=[])

        rag_latency_ms = int((time.time() - rag_started_at) * 1000)
        answer = final_state.get("answer", "")
        sources = final_state.get("sources", [])

        # 6. 安全护栏：输出过滤
        answer = _guardrails.filter_output(answer)

        # 7. 仅首轮且自检通过的 RAG 结果写入缓存，生成失败分支不会缓存降级摘要。
        result_data = {"answer": answer, "sources": sources}
        if not history and final_state.get("check_passed", False):
            _semantic_cache.put(request.club_id, request.message, result_data)

        logger.info(
            f"Chat completed | session={session_id} | trace={trace_id} | "
            f"rag_latency_ms={rag_latency_ms} | "
            f"coarse={len(final_state.get('coarse_results', []))} | "
            f"fine={len(final_state.get('fine_results', []))} | "
            f"retry={final_state.get('retry_count', 0)} | "
            f"check_passed={final_state.get('check_passed', False)}"
        )

        # 8. 保存对话历史
        try:
            redis_client.rpush(history_key, json.dumps({"role": "user", "content": request.message}))
            redis_client.rpush(history_key, json.dumps({"role": "assistant", "content": answer}))
            redis_client.ltrim(history_key, -20, -1)  # 限制最近 20 条，防止无限增长
            redis_client.expire(history_key, 86400)
        except Exception as e:
            logger.warning(f"Redis operation failed: {e}")

        return ChatResponse(session_id=session_id, answer=answer, sources=sources)
    except Exception as e:
        logger.error(f"Chat error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="服务内部错误，请稍后重试")


@router.post("/stream")
async def chat_stream(request: ChatRequest):
    """SSE 真流式对话端点"""
    return StreamingResponse(stream_event_generator(request), media_type="text/event-stream")


@router.post("/sessions")
async def create_session(request: SessionCreateRequest):
    session_id = str(uuid.uuid4())
    return {
        "session_id": session_id,
        "club_id": request.club_id,
        "created_at": datetime.datetime.now().isoformat()
    }


@router.get("/models")
async def get_model_status():
    """查看模型路由器状态（主/备模型、调用统计）"""
    from agents.model_router import get_model_router
    router_inst = get_model_router()
    profiles = {}
    for name, p in router_inst._profiles.items():
        profiles[name] = {
            "model": p.model,
            "base_url": p.base_url,
            "supports_fc": p.supports_function_calling,
            "supports_thinking": p.supports_thinking,
        }
    return {
        "profiles": profiles,
        "task_routing": router_inst._task_routing,
        "stats": router_inst.get_stats()
    }
