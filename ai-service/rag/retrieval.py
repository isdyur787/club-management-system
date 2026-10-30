# 文件职责：并行向量/BM25 召回、RRF 融合及分层缓存，按集合和过滤条件检索知识。
import numpy as np
import logging
import threading
import time
import json
from typing import List, Dict, Optional
from concurrent.futures import ThreadPoolExecutor
from rank_bm25 import BM25Okapi
import jieba
from config import RRF_K, VECTOR_SEARCH_TOP_K, BM25_SEARCH_TOP_K, RETRIEVAL_TOP_K, BM25_TTL, SECTION_TYPE_KEYWORDS
from store.chroma_store import ChromaVectorStore, chroma_where
from store.vector_store import SearchResult
from rag.ingestion import get_embedding_model
from rag.latency import get_retrieval_cache, get_embedding_cache

logger = logging.getLogger(__name__)

# 检索并行线程池
_retrieval_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="retrieval")

# ========== BM25 索引缓存 ==========
# 避免每次查询都重新加载全部文档并构建 BM25 索引
_bm25_cache: Dict[str, dict] = {}  # collection -> {"bm25": BM25, "docs": list, "ids": list, "metadatas": list, "ts": float}
_bm25_lock = threading.Lock()
_BM25_TTL = BM25_TTL  # 从 config.py 读取，默认 5 分钟

def detect_section_type(query: str) -> Optional[str]:
    for section_type, keywords in SECTION_TYPE_KEYWORDS.items():
        if any(kw in query for kw in keywords):
            return section_type
    return None

def hybrid_search(collection: str, query: str,
                  where: Dict = None,
                  top_k: int = None) -> List[Dict]:
    """
    混合检索
    
    参数：
        top_k: 返回数量。None 时使用 COARSE_TOP_K（粗排，给 Reranker 用）
    """
    if top_k is None:
        from config import COARSE_TOP_K
        top_k = COARSE_TOP_K
    
    effective_where = dict(where) if where else {}
    section_type = detect_section_type(query)
    if section_type:
        effective_where["section_type"] = section_type

    # 检查检索 TTL 缓存（避免短时间内重复检索）
    retrieval_cache = get_retrieval_cache()
    # 完整过滤条件也参与缓存键，避免同一集合下不同文档类型/反馈维度互用结果。
    cache_collection = collection + ":" + json.dumps(effective_where, sort_keys=True, ensure_ascii=False)
    cached = retrieval_cache.get(cache_collection, query, top_k)
    if cached is not None:
        logger.debug(f"Retrieval cache hit for: {query[:50]}")
        return cached

    # 查询 Embedding 缓存（避免重复向量化）
    emb_cache = get_embedding_cache()
    query_embedding = emb_cache.get(query)
    if query_embedding is None:
        model = get_embedding_model()
        query_embedding = model.encode([query])[0].tolist()
        emb_cache.put(query, query_embedding)

    store = ChromaVectorStore()

    # 向量检索 + BM25 并行执行（减少总延迟）
    vector_future = _retrieval_executor.submit(
        store.search, collection, query_embedding,
        VECTOR_SEARCH_TOP_K, effective_where
    )
    bm25_future = _retrieval_executor.submit(
        bm25_search, collection, query,
        BM25_SEARCH_TOP_K, effective_where
    )
    vector_results = vector_future.result(timeout=20)
    bm25_results = bm25_future.result(timeout=20)

    fused = rrf_fusion(vector_results, bm25_results, k=RRF_K)
    results = fused[:top_k]
    
    # 降级：如果 section_type 过滤导致无结果，去掉 section_type 重新检索
    if not results and section_type:
        # 仅撤销自动识别的 section_type，保留调用方传入的其他过滤条件和社团集合。
        fallback_where = dict(where) if where else {}
        # 同样并行执行
        vf2 = _retrieval_executor.submit(
            store.search, collection, query_embedding,
            VECTOR_SEARCH_TOP_K, fallback_where
        )
        bf2 = _retrieval_executor.submit(
            bm25_search, collection, query,
            BM25_SEARCH_TOP_K, fallback_where
        )
        fused = rrf_fusion(vf2.result(timeout=20), bf2.result(timeout=20), k=RRF_K)
        results = fused[:top_k]
        logger.info(f"section_type='{section_type}' 过滤无结果，已降级为不带过滤检索")

    # 写入检索 TTL 缓存
    retrieval_cache.put(cache_collection, query, top_k, results)

    # 若片段声明父文档，尽力提升上下文；父文档读取异常时仍返回已召回的片段。
    try:
        parent_ids = list({r.get("metadata", {}).get("parent_id") for r in results if r.get("metadata", {}).get("parent_id")})
        if parent_ids:
            store = ChromaVectorStore()
            col = store._get_collection(collection)
            parent_docs = col.get(ids=parent_ids, include=["documents", "metadatas"]) 
            parent_map = {}
            if parent_docs and parent_docs.get("ids"):
                for pid, doc, meta in zip(parent_docs["ids"], parent_docs.get("documents", []), parent_docs.get("metadatas", [])):
                    parent_map[pid] = {"content": doc, "metadata": meta}

            promoted = []
            for r in results:
                parent_id = r.get("metadata", {}).get("parent_id")
                if parent_id and parent_id in parent_map:
                    promoted.append({
                        "id": parent_id,
                        "content": parent_map[parent_id]["content"],
                        "metadata": parent_map[parent_id]["metadata"],
                        "score": r.get("score", 0)
                    })
                else:
                    promoted.append(r)
            results = promoted
    except Exception as e:
        logger.warning(f"Parent document promotion failed: {e}")

    return results

def _get_bm25_index(collection: str, where: Dict = None) -> Optional[dict]:
    """获取或构建 BM25 索引（带缓存 + 线程安全）"""
    cache_key = collection if not where else collection + ":" + json.dumps(where, sort_keys=True, ensure_ascii=False)

    with _bm25_lock:
        now = time.time()
        cached = _bm25_cache.get(cache_key)
        if cached and (now - cached["ts"]) < _BM25_TTL:
            return cached

        # 缓存未命中或已过期，在锁内构建，防止多线程重复构建
        try:
            col = ChromaVectorStore()._get_collection(collection)
            get_kwargs = {"include": ["documents", "metadatas"]}
            if where:
                get_kwargs["where"] = chroma_where(where)
            all_docs = col.get(**get_kwargs)
            if not all_docs["documents"]:
                return None

            tokenized_docs = [list(jieba.cut(doc)) for doc in all_docs["documents"]]
            bm25 = BM25Okapi(tokenized_docs)

            result = {
                "bm25": bm25,
                "ids": all_docs["ids"],
                "documents": all_docs["documents"],
                "metadatas": all_docs["metadatas"],
                "ts": now
            }

            _bm25_cache[cache_key] = result

            return result
        except Exception as e:
            logger.warning(f"BM25 index build failed: {e}")
            return None


def bm25_search(collection: str, query: str,
                top_k: int = 20, where: Dict = None) -> List[SearchResult]:
    try:
        index = _get_bm25_index(collection, where)
        if not index:
            return []

        tokenized_query = list(jieba.cut(query))
        scores = index["bm25"].get_scores(tokenized_query)

        top_indices = np.argsort(scores)[::-1][:top_k]
        results = []
        for idx in top_indices:
            if scores[idx] > 0:
                results.append(SearchResult(
                    id=index["ids"][idx],
                    content=index["documents"][idx],
                    metadata=index["metadatas"][idx],
                    score=float(scores[idx])
                ))
        return results
    except Exception as e:
        logger.warning(f"BM25 search failed: {e}")
        return []

def rrf_fusion(vector_results: List[SearchResult],
               bm25_results: List[SearchResult],
               k: int = 60) -> List[Dict]:
    # 累加各路排名的倒数分数，避免直接比较 BM25 与向量相似度的不同原始分值尺度。
    doc_scores = {}

    for rank, result in enumerate(vector_results):
        if result.id not in doc_scores:
            doc_scores[result.id] = {
                "score": 0, "content": result.content,
                "metadata": result.metadata
            }
        doc_scores[result.id]["score"] += 1.0 / (k + rank + 1)

    for rank, result in enumerate(bm25_results):
        if result.id not in doc_scores:
            doc_scores[result.id] = {
                "score": 0, "content": result.content,
                "metadata": result.metadata
            }
        doc_scores[result.id]["score"] += 1.0 / (k + rank + 1)

    sorted_docs = sorted(doc_scores.items(), key=lambda x: x[1]["score"],
                         reverse=True)
    return [{"id": id, **data} for id, data in sorted_docs]
