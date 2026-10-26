"""
chat_deps.py — 共享依赖与辅助函数

从 chat.py 拆分出的全局资源（redis、线程池、guardrails、语义缓存）
和前置校验 / FC 参数校验 / 文本工具函数。
"""

import logging
import os
import redis
import json
import threading
from typing import Optional, List, Dict, Any
from concurrent.futures import ThreadPoolExecutor
from models.schemas import ChatRequest, ChatResponse
from rag.generator import self_check_answer
from rag.guardrails import Guardrails
from rag.semantic_cache import SemanticCache
from config import REDIS_URL

logger = logging.getLogger(__name__)

# ========== 全局资源 ==========

redis_client = redis.from_url(
    REDIS_URL, decode_responses=True,
    socket_timeout=5, socket_connect_timeout=3, retry_on_timeout=True,
)

_executor = ThreadPoolExecutor(max_workers=int(os.environ.get('LLM_WORKERS', 8)))
_guardrails = Guardrails()
_semantic_cache = SemanticCache()

def load_history(session_id: str) -> List[Dict]:
    try:
        return [json.loads(m) for m in redis_client.lrange(f"chat:{session_id}:history", 0, -1)]
    except Exception as e:
        logger.warning("Read chat history failed: %s", e)
        return []

def save_history(session_id: str, question: str, answer: str):
    try:
        key = f"chat:{session_id}:history"
        with redis_client.pipeline() as pipeline:
            pipeline.rpush(key, json.dumps({"role": "user", "content": question}, ensure_ascii=False),
                           json.dumps({"role": "assistant", "content": answer}, ensure_ascii=False))
            pipeline.ltrim(key, -20, -1)
            pipeline.expire(key, 86400)
            pipeline.execute()
    except Exception as e:
        logger.warning("Save chat history failed: %s", e)


# ========== 业务前置校验 ==========

def _validate_request_guardrails(message: str, club_id: int, rate_limit_key: Optional[str] = None):
    """统一前置校验：基础安全 + 话题白名单 + 限流"""
    is_safe, reason = _guardrails.check_input(message)
    if not is_safe:
        return False, reason

    topic_allowed, topic_reason = _guardrails.check_topic_allowed(message)
    if not topic_allowed:
        return False, topic_reason

    rate_ok, rate_reason = _guardrails.check_rate_limit(rate_limit_key)
    if not rate_ok:
        return False, rate_reason

    return True, ""


# ========== Function Calling 安全策略 ==========

def _get_fc_default_arguments(func_name: str, club_id: int) -> Dict[str, int]:
    """为工具参数注入强制默认值，避免模型越权改写关键参数"""
    try:
        from tools.tool_registry import ToolRegistry, ensure_tools_initialized
        ensure_tools_initialized()
        schema = ToolRegistry._tools.get(func_name, {}).get("function", {}).get("parameters", {})
        properties = schema.get("properties", {})
        if "club_id" in properties:
            return {"club_id": club_id}
    except Exception:
        pass
    return {}


def _validate_fc_tool_arguments(func_name: str, func_args: Dict[str, Any], club_id: int) -> Dict[str, Any]:
    """校验并修正 LLM 给出的工具参数，防止越权/异常参数"""
    validated_args = dict(func_args)
    default_arguments = _get_fc_default_arguments(func_name, club_id)
    for key, value in default_arguments.items():
        if key not in validated_args or validated_args[key] != value:
            if key in validated_args:
                logger.warning(f"FC arg override blocked: {func_name}.{key} requested={validated_args[key]} forced={value}")
            validated_args[key] = value
    return validated_args


def _self_check_fc_answer(answer: str, tool_names: List[str]) -> Dict[str, Any]:
    """为 Function Calling 结果补齐轻量自检（与 RAG 路径对齐）"""
    if not answer:
        return {"passed": True, "confidence": 0.5}

    fallback_docs = [
        {"content": answer, "metadata": {"title": name, "doc_type": "tool_result"}}
        for name in tool_names
    ]

    try:
        result = self_check_answer(answer, fallback_docs)
        return result
    except Exception as e:
        logger.warning(f"FC self-check failed: {e}")
        return {"passed": True, "confidence": 0.5}


# ========== 文本工具函数 ==========

def _is_garbled(text: str) -> bool:
    """检测文本是否为乱码"""
    if '???' in text or '\ufffd' in text:
        return True
    chinese_chars = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    if len(text) > 50 and chinese_chars / len(text) < 0.1:
        return True
    return False


def _filter_garbled_results(results: List[Dict]) -> List[Dict]:
    """过滤掉乱码检索结果"""
    filtered = [r for r in results if not _is_garbled(r.get('content', ''))]
    if len(filtered) < len(results):
        logger.warning(f"过滤了 {len(results) - len(filtered)} 个乱码 chunk")
    return filtered
