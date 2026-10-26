import os
import logging
import threading
import secrets
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from routes import chat, report, experience, knowledge, notification, registration
from routes import content_agent, insights_agent, sentiment_agent

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s [%(name)s] %(message)s'
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """服务生命周期管理（替代已废弃的 on_event）"""
    # ========== Startup ==========
    os.makedirs("./data/chroma", exist_ok=True)
    os.makedirs("./data/reports", exist_ok=True)
    os.makedirs("./data/uploads", exist_ok=True)
    logger.info("AI Service started")

    # 后台线程预热模型（不阻塞启动）
    def _warmup():
        try:
            from rag.latency import warmup_models
            warmup_models()
            logger.info("Model warmup completed")
        except Exception as e:
            logger.warning(f"Model warmup failed (non-critical): {e}")

    warmup_thread = threading.Thread(target=_warmup, daemon=True, name="warmup")
    warmup_thread.start()

    yield  # 服务运行中

    # ========== Shutdown ==========
    logger.info("AI Service shutting down...")
    # 关闭线程池
    try:
        from routes.chat import _executor
        _executor.shutdown(wait=False)
        logger.info("ThreadPoolExecutor shutdown")
    except Exception:
        pass
    # 关闭 Redis 连接
    try:
        from routes.chat import redis_client
        redis_client.close()
        logger.info("Redis connection closed")
    except Exception:
        pass


app = FastAPI(title="Club AI Service", version="1.0.0", lifespan=lifespan)


# ========== 认证中间件 ==========
# Java 端通过内部 HTTP 调用 AI 服务，携带 X-Internal-Key 请求头
# 管理端点（admin/*）需要额外验证，防止外部直接访问

INTERNAL_API_KEY = os.getenv("AI_INTERNAL_API_KEY")
if not INTERNAL_API_KEY:
    raise ValueError("AI_INTERNAL_API_KEY environment variable is required. Set it in .env or system environment.")


class AuthMiddleware(BaseHTTPMiddleware):
    """全局认证中间件：保护管理端点"""
    # 不需要认证的路径
    PUBLIC_PATHS = {"/health", "/docs", "/openapi.json"}
    # 需要 Internal-Key 认证的管理路径前缀
    ADMIN_PREFIXES = ("/knowledge/admin",)

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        # 公开路径直接放行
        if path in self.PUBLIC_PATHS:
            return await call_next(request)

        # 管理端点需要 Internal-Key
        api_key = request.headers.get("X-Internal-Key", "")
        if not secrets.compare_digest(api_key, INTERNAL_API_KEY):
            return JSONResponse(status_code=403, content={"detail": "Forbidden: invalid internal API key"})

        return await call_next(request)


app.add_middleware(AuthMiddleware)

app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000").split(","),
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(chat.router, prefix="/chat", tags=["对话"])
app.include_router(report.router, prefix="/report", tags=["成果报告"])
app.include_router(experience.router, prefix="/experience", tags=["经验参考"])
app.include_router(knowledge.router, prefix="/knowledge", tags=["知识库"])
app.include_router(notification.router, prefix="/notification", tags=["智能通知"])
app.include_router(registration.router, prefix="/registration", tags=["报名审核"])
app.include_router(content_agent.router, prefix="/content-agent", tags=["文案生成 Agent"])
app.include_router(insights_agent.router, prefix="/insights-agent", tags=["数据洞察 Agent"])
app.include_router(sentiment_agent.router, prefix="/sentiment-agent", tags=["情感分析 Agent"])

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/latency/stats")
async def latency_stats():
    """延迟优化状态监控端点（面试亮点：可观测性）"""
    from rag.latency import get_llm_limiter, get_retrieval_cache, get_embedding_cache
    from routes.chat import _semantic_cache
    return {
        "llm_concurrency": get_llm_limiter().get_stats(),
        "retrieval_cache": get_retrieval_cache().get_stats(),
        "embedding_cache": get_embedding_cache().get_stats(),
        "semantic_cache": _semantic_cache.get_stats()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
