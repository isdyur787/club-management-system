import os
from pathlib import Path
from dotenv import load_dotenv

# 加载 .env 文件（项目根目录）
_env_file = Path(__file__).parent.parent / ".env"
if _env_file.exists():
    load_dotenv(_env_file)

# ========== 模型配置（Model Router 调度） ==========

# 主 LLM：DeepSeek V4-Flash（支持原生 Function Calling）
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-v4-flash")
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.7"))
LLM_TIMEOUT = int(os.getenv("LLM_TIMEOUT", "60"))

# 备用 LLM：MiMo v2.5（主模型不可用时降级）
FALLBACK_API_KEY = os.getenv("FALLBACK_API_KEY", "")
FALLBACK_BASE_URL = os.getenv("FALLBACK_BASE_URL", "https://api.xiaomimimo.com/v1")
FALLBACK_MODEL = os.getenv("FALLBACK_MODEL", "mimo-v2.5")

# 多模态 LLM（图片描述，独立模型）
MULTIMODAL_API_KEY = os.getenv("MULTIMODAL_API_KEY") or LLM_API_KEY
MULTIMODAL_BASE_URL = os.getenv("MULTIMODAL_BASE_URL") or LLM_BASE_URL
MULTIMODAL_MODEL = os.getenv("MULTIMODAL_MODEL") or LLM_MODEL

# Embedding 模型
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
# 本地缓存路径（优先使用，避免联网超时）
_local_bge = os.path.join(os.path.expanduser("~"), ".cache", "huggingface", "hub", "models--BAAI--bge-large-zh-v1.5", "snapshots")
if "EMBEDDING_MODEL" not in os.environ and os.path.isdir(_local_bge):
    # 取最新的 snapshot
    _snapshots = sorted(os.listdir(_local_bge))
    if _snapshots:
        _local_path = os.path.join(_local_bge, _snapshots[-1])
        # 验证包含必要文件
        if os.path.isfile(os.path.join(_local_path, "config.json")):
            EMBEDDING_MODEL = _local_path
EMBEDDING_DEVICE = os.getenv("EMBEDDING_DEVICE", "cpu")

# ChromaDB
_data_dir = Path(__file__).resolve().parent / "data"
CHROMA_PERSIST_DIR = str(_data_dir / "chroma")

# 检索参数
CHUNK_SIZE = 512
CHUNK_OVERLAP = 64
VECTOR_SEARCH_TOP_K = 20
BM25_SEARCH_TOP_K = 20
RETRIEVAL_TOP_K = 5
RRF_K = 60

# MySQL
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD")
if not MYSQL_PASSWORD:
    raise ValueError("MYSQL_PASSWORD environment variable is required. Set it in .env or system environment.")
if not LLM_API_KEY:
    raise ValueError("LLM_API_KEY environment variable is required. Set it in .env or system environment.")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "club_management")

# Redis
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/1")

# 文件存储
REPORT_OUTPUT_DIR = str(_data_dir / "reports")
UPLOAD_DIR = str(_data_dir / "uploads")

# Reranker 模型
RERANKER_MODEL = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-base")
RERANKER_DEVICE = "cpu"
RERANKER_TOP_K = 3

# 检索粗排数量（给 Reranker 精排用）
COARSE_TOP_K = 10

# 自检置信度阈值
SELF_CHECK_THRESHOLD = 0.6

# LLM 并发控制
LLM_MAX_CONCURRENCY = int(os.getenv("LLM_MAX_CONCURRENCY", "3"))

# BM25 索引缓存过期时间（秒）
BM25_TTL = int(os.getenv("BM25_TTL", "300"))  # 默认 5 分钟

# 区块类型检测关键词
SECTION_TYPE_KEYWORDS = {
    "problems": ["问题", "不足", "困难", "体验不好", "缺点", "失误", "踩坑"],
    "suggestions": ["改进", "建议", "下次", "优化", "提升", "怎么办", "怎么改"],
    "reflections": ["收获", "学到", "心得", "启发", "成长", "理解", "感悟"],
}
