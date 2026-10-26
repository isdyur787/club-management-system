"""
chat_stream.py — SSE 流式端点逻辑

包含同步 RAG 检索管道、真流式生成器、SSE event_generator。
"""

import json
import asyncio
import logging
import re
import uuid
import time
from typing import List, Dict, AsyncGenerator

from routes.chat_deps import (
    redis_client, _executor, _guardrails, _semantic_cache,
    _validate_request_guardrails, _self_check_fc_answer, _filter_garbled_results,
)
from routes.chat_agent import _try_function_calling
from rag.retrieval import hybrid_search
from rag.reranker import rerank
from rag.knowledge_base import get_collection_name
from rag.generator import build_rag_messages
from rag.query_rewriter import rewrite_query
from rag.latency import get_llm_limiter, get_retrieval_cache
from config import RERANKER_TOP_K, COARSE_TOP_K

logger = logging.getLogger(__name__)


# ========== 辅助函数 ==========

def _split_sentences(text: str) -> list:
    """将文本按句子分割"""
    sentences = re.split(r'(?<=[。！？\n])', text)
    return [s for s in sentences if s.strip()]


# ========== SSE 真流式输出 ==========

def _retrieve_context(question: str, club_id: int, history: List[Dict]) -> tuple:
    """
    同步执行 RAG 检索管道，返回上下文文档和来源信息

    管道：粗排检索 → 精排 → 相关性判断 → [查询改写]

    返回：
        (fine_results, sources) — 检索结果和来源元数据
    """
    # 粗排
    collection = get_collection_name(club_id)
    coarse = hybrid_search(collection, question, top_k=COARSE_TOP_K)
    coarse = _filter_garbled_results(coarse or [])

    # 精排
    fine = []
    if coarse:
        try:
            fine = rerank(question, coarse, top_k=RERANKER_TOP_K)
        except Exception as e:
            logger.warning(f"Rerank failed: {e}")
            fine = coarse[:RERANKER_TOP_K]

    # 查询改写（仅当精排分数较低时）
    if fine:
        max_score = max(doc.get("rerank_score", 0) for doc in fine)
        if max_score < 1.0:
            rewritten = rewrite_query(question, history)
            if rewritten != question:
                try:
                    new_coarse = hybrid_search(collection, rewritten, top_k=COARSE_TOP_K)
                    new_fine = rerank(rewritten, new_coarse or [], top_k=RERANKER_TOP_K)
                    new_max = max((d.get("rerank_score", 0) for d in new_fine), default=0)
                    if new_max > max_score:
                        fine = new_fine
                except Exception as e:
                    logger.warning(f"Query rewrite retrieval failed: {e}")

    sources = []
    for doc in fine:
        sources.append({
            "doc_id": doc.get("id", ""),
            "title": doc.get("metadata", {}).get("title", ""),
            "snippet": doc["content"][:200] + "..." if len(doc["content"]) > 200 else doc["content"],
            "doc_type": doc.get("metadata", {}).get("doc_type", "")
        })

    return fine, sources


def _stream_generate(question: str, fine_results: List[Dict],
                     history: List[Dict]):
    """
    真流式生成：使用 OpenAI stream=True，逐 token 产出

    设计模式：Iterator / Generator — 流式产出 token，消费者逐条处理
    """
    from agents.model_router import get_model_router

    messages = build_rag_messages(question, fine_results, history)

    router = get_model_router()
    result = router.call_with_fallback(
        task_type="rag",
        messages=messages,
        temperature=0.7,
        stream=True
    )

    # 流式输出：逐 chunk 产出 token
    for chunk in result["response"]:
        delta = chunk.choices[0].delta if chunk.choices else None
        if delta and delta.content:
            yield delta.content


async def stream_event_generator(request) -> AsyncGenerator[str, None]:
    """
    SSE 事件生成器：chat_stream 端点的核心逻辑。

    按顺序执行：前置校验 → 语义缓存 → FC → RAG 检索 → 流式生成 → 输出过滤/缓存/历史。
    """
    # 1. 统一前置校验（安全 + 话题 + 限流）
    is_safe, reason = _validate_request_guardrails(
        request.message,
        request.club_id,
        rate_limit_key=f"stream:{request.club_id}:{request.session_id or 'anonymous'}"
    )
    if not is_safe:
        yield f"data: {json.dumps({'type': 'error', 'content': reason}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
        return

    session_id = request.session_id or str(uuid.uuid4())
    from routes.chat_deps import load_history, save_history
    history = load_history(session_id)

    # 2. 语义缓存
    cached = _semantic_cache.get(request.club_id, request.message) if not history else None
    if cached:
        save_history(session_id, request.message, cached.get("answer", ""))
        # 缓存命中也模拟流式输出（体验一致）
        for sentence in _split_sentences(cached.get("answer", "")):
            yield f"data: {json.dumps({'type': 'token', 'content': sentence}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.02)
        yield f"data: {json.dumps({'type': 'done', 'sources': cached.get('sources', [])}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
        return

    # 3. Function Calling（同步，因 FC 不支持流式）
    fc_result = await asyncio.to_thread(_try_function_calling, request.message, request.club_id, history)
    if fc_result:
        fc_result["answer"] = _guardrails.filter_output(fc_result["answer"])
        save_history(session_id, request.message, fc_result["answer"])
        for sentence in _split_sentences(fc_result["answer"]):
            yield f"data: {json.dumps({'type': 'token', 'content': sentence}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.02)
        yield f"data: {json.dumps({'type': 'done', 'sources': fc_result['sources']}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
        return

    # 4. 获取历史
    history_key = f"chat:{session_id}:history"

    # 5. RAG 检索（分阶段状态提示 + 并行检索）
    yield f"data: {json.dumps({'type': 'status', 'content': '正在检索知识库...'}, ensure_ascii=False)}\n\n"

    # 检查检索缓存命中情况
    retrieval_cache = get_retrieval_cache()
    collection = get_collection_name(request.club_id)
    cache_check = retrieval_cache.get(collection, request.message, COARSE_TOP_K)
    if cache_check is not None:
        yield f"data: {json.dumps({'type': 'status', 'content': '检索缓存命中，正在精排...'}, ensure_ascii=False)}\n\n"

    try:
        loop = asyncio.get_running_loop()
        fine_results, sources = await asyncio.wait_for(
            loop.run_in_executor(
                _executor, _retrieve_context,
                request.message, request.club_id, history
            ),
            timeout=30.0
        )
    except asyncio.TimeoutError:
        yield f"data: {json.dumps({'type': 'error', 'content': '检索超时，请稍后重试'}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
        return

    if not fine_results:
        yield f"data: {json.dumps({'type': 'token', 'content': '抱歉，知识库中暂无相关内容。请先上传文档或等待知识库更新。'}, ensure_ascii=False)}\n\n"
        yield f"data: {json.dumps({'type': 'done', 'sources': []}, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
        return

    # 6. 真流式生成（分阶段状态 + LLM 并发控制）
    yield f"data: {json.dumps({'type': 'status', 'content': f'检索到 {len(fine_results)} 个相关文档，正在生成回答...'}, ensure_ascii=False)}\n\n"

    full_answer = ""
    limiter = get_llm_limiter()
    await limiter.acquire()
    try:
        loop = asyncio.get_running_loop()
        # 在线程池中运行同步流式生成器，通过队列桥接到 async
        token_queue = asyncio.Queue()

        def _run_stream():
            try:
                for token in _stream_generate(request.message, fine_results, history):
                    loop.call_soon_threadsafe(token_queue.put_nowait, token)
            except Exception as e:
                logger.error(f"Stream generation error: {e}")
                loop.call_soon_threadsafe(token_queue.put_nowait, e)
            finally:
                loop.call_soon_threadsafe(token_queue.put_nowait, None)  # 结束信号

        loop.run_in_executor(_executor, _run_stream)

        while True:
            token = await token_queue.get()
            if token is None:
                break
            if isinstance(token, Exception):
                raise RuntimeError("模型流式生成失败") from token
            full_answer += token
            yield f"data: {json.dumps({'type': 'token', 'content': token}, ensure_ascii=False)}\n\n"

    except Exception as e:
        logger.error(f"Stream error: {e}")
        if not full_answer:
            yield f"data: {json.dumps({'type': 'error', 'content': '生成回答时出错，请稍后重试'}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"
            return
    finally:
        limiter.release()

    # 7. 输出过滤 + 自检补强 + 缓存 + 历史
    full_answer = _guardrails.filter_output(full_answer)

    stream_self_check = _self_check_fc_answer(full_answer, list({s.get('title', '') for s in sources if s.get('title')}))
    stream_check_passed = bool(stream_self_check.get("passed", True))
    if not stream_check_passed:
        full_answer = f"⚠️ 以下回答可能不完全准确，仅供参考：\n\n{full_answer}"

    cache_payload = {"answer": full_answer, "sources": sources}
    if not history and stream_check_passed:
        _semantic_cache.put(request.club_id, request.message, cache_payload)
    try:
        redis_client.rpush(history_key, json.dumps({"role": "user", "content": request.message}))
        redis_client.rpush(history_key, json.dumps({"role": "assistant", "content": full_answer}))
        redis_client.ltrim(history_key, -20, -1)
        redis_client.expire(history_key, 86400)
    except Exception as e:
        logger.warning(f"Redis operation failed: {e}")

    yield f"data: {json.dumps({'type': 'done', 'sources': sources}, ensure_ascii=False)}\n\n"
    yield "data: [DONE]\n\n"
