"""
语义缓存（Semantic Cache）— 基于向量相似度的问答缓存

设计模式：Decorator 模式 — 包裹在 RAG 管道外层，透明加速
"""
import json
import uuid
import logging
from datetime import datetime
from typing import Optional, Dict
from config import CHROMA_PERSIST_DIR

logger = logging.getLogger(__name__)

CACHE_COLLECTION = "semantic_cache"
DEFAULT_THRESHOLD = 0.88
DEFAULT_TTL_HOURS = 24
MAX_CACHE_SIZE = 1000  # 缓存集合最大条目数
EVICTION_BATCH = 100   # 每次淘汰的条目数


class SemanticCache:
    """基于向量相似度的语义缓存"""

    def __init__(self, threshold: float = DEFAULT_THRESHOLD, ttl_hours: int = DEFAULT_TTL_HOURS):
        self.threshold = threshold
        self.ttl_hours = ttl_hours
        self._hits = 0
        self._misses = 0

    def _evict_if_needed(self, store) -> None:
        """LRU 淘汰：集合超过阈值时删除最久未访问的条目"""
        try:
            count = store.count(CACHE_COLLECTION)
            if count <= MAX_CACHE_SIZE:
                return

            # 获取所有条目的 ID 和元数据
            col = store._get_collection(CACHE_COLLECTION)
            all_data = col.get(include=["metadatas"])

            if not all_data or not all_data["ids"]:
                return

            # 按 last_accessed_at 排序，找出最久未访问的条目（真正 LRU）
            entries = []
            for i, doc_id in enumerate(all_data["ids"]):
                metadata = (all_data["metadatas"] or [{}])[i] or {}
                # 优先用 last_accessed_at，回退到 cached_at
                try:
                    answer_data = json.loads(metadata.get("answer_json", "{}"))
                    last_accessed = answer_data.get("last_accessed_at") or answer_data.get("cached_at", "9999-12-31")
                except (json.JSONDecodeError, TypeError):
                    last_accessed = "9999-12-31"
                entries.append((last_accessed, doc_id))

            entries.sort(key=lambda x: x[0])
            to_evict = [doc_id for _, doc_id in entries[:EVICTION_BATCH]]
            store.delete_documents(CACHE_COLLECTION, to_evict)
            logger.info(f"Evicted {len(to_evict)} stale cache entries (total was {count})")
        except Exception as e:
            logger.warning(f"Cache eviction failed: {e}")

    def _update_access_time(self, store, doc_id: str, cached_data: dict) -> None:
        """更新缓存条目的 last_accessed_at 字段"""
        try:
            cached_data["last_accessed_at"] = datetime.now().isoformat()
            col = store._get_collection(CACHE_COLLECTION)
            col.update(
                ids=[doc_id],
                metadatas=[{
                    "answer_json": json.dumps(cached_data, ensure_ascii=False),
                    "club_id": cached_data.get("club_id", 0),
                    "query_hash": str(hash(cached_data.get("original_query", ""))),
                }]
            )
        except Exception as e:
            logger.warning(f"Failed to update access time for cache entry {doc_id}: {e}")

    def get(self, club_id: int, query: str) -> Optional[Dict]:
        """查询缓存：如果找到语义相似的问题（相似度 > threshold），直接返回缓存答案"""
        try:
            from store.chroma_store import ChromaVectorStore
            from rag.ingestion import get_embedding_model

            query_length = len(query.strip())
            dynamic_threshold = self.threshold
            if query_length < 10:
                dynamic_threshold = max(dynamic_threshold, 0.92)
            elif query_length > 50:
                dynamic_threshold = min(dynamic_threshold, 0.85)

            store = ChromaVectorStore()
            count = store.count(CACHE_COLLECTION)
            if count == 0:
                return None

            model = get_embedding_model()
            query_embedding = model.encode([query])[0].tolist()

            results = store.search(CACHE_COLLECTION, query_embedding, top_k=1,
                                   where={"club_id": club_id})

            if not results:
                return None

            result = results[0]
            similarity = result.score  # cosine similarity (0-1)

            if similarity >= dynamic_threshold:
                # 答案存储在 metadata 的 answer_json 字段中
                cached = json.loads(result.metadata.get("answer_json", "{}"))

                # TTL 检查
                cached_at = datetime.fromisoformat(cached.get("cached_at", "2000-01-01"))
                if (datetime.now() - cached_at).total_seconds() < self.ttl_hours * 3600:
                    self._hits += 1
                    logger.info(f"Cache hit (similarity={similarity:.3f}) for query: {query[:50]}")
                    cached["cache_hit"] = True
                    cached["cache_similarity"] = similarity
                    # 更新 last_accessed_at 以实现真正 LRU
                    self._update_access_time(store, result.id, cached)
                    return cached

            self._misses += 1
            logger.debug(f"Cache miss: best similarity={similarity:.3f} < threshold={dynamic_threshold}")
            return None
        except Exception as e:
            self._misses += 1
            logger.warning(f"Cache get failed: {e}")
            return None

    def put(self, club_id: int, query: str, answer: Dict):
        """写入缓存：文档存查询文本（用于向量匹配），答案存 metadata"""
        try:
            from store.chroma_store import ChromaVectorStore
            from rag.ingestion import get_embedding_model

            store = ChromaVectorStore()
            model = get_embedding_model()

            # LRU 淘汰：写入前检查集合大小
            self._evict_if_needed(store)

            # Embed 查询文本（而非答案 JSON）— 确保搜索时 query↔query 匹配
            query_embedding = model.encode([query])[0].tolist()

            cache_data = {
                **answer,
                "cached_at": datetime.now().isoformat(),
                "last_accessed_at": datetime.now().isoformat(),
                "original_query": query,
                "club_id": club_id,
            }

            store.add_documents(
                CACHE_COLLECTION,
                documents=[query],  # ← 存查询文本用于向量匹配
                metadatas=[{
                    "club_id": club_id,
                    "query_hash": str(hash(query)),
                    "answer_json": json.dumps(cache_data, ensure_ascii=False)  # ← 答案存 metadata
                }],
                ids=[str(uuid.uuid4())]
            )
            logger.info(f"Cache stored for query: {query[:50]}")
        except Exception as e:
            logger.warning(f"Cache put failed: {e}")

    def get_stats(self) -> dict:
        total = self._hits + self._misses
        return {
            "hits": self._hits,
            "misses": self._misses,
            "hit_rate": f"{self._hits / max(total, 1) * 100:.1f}%"
        }
