# 文件职责：懒加载精排模型，为查询和候选正文配对打分；失败时保留粗排的前 Top-K。
import logging
import threading
from typing import List, Dict

logger = logging.getLogger(__name__)

_reranker = None
_reranker_lock = threading.Lock()

def get_reranker():
    """懒加载 Reranker 模型，首次加载后缓存（双重检查锁定）"""
    global _reranker
    if _reranker is None:
        with _reranker_lock:
            if _reranker is None:  # 双重检查锁定
                import os
                from FlagEmbedding import FlagReranker
                from config import RERANKER_MODEL, RERANKER_DEVICE
                logger.info(f"Loading reranker model: {RERANKER_MODEL}")
                _reranker = FlagReranker(RERANKER_MODEL, devices=[RERANKER_DEVICE], use_fp16=RERANKER_DEVICE != "cpu")
    return _reranker

def rerank(query: str, candidates: List[Dict], top_k: int = 3) -> List[Dict]:
    """
    对候选文档进行重排序
    
    参数：
        query: 用户查询
        candidates: 候选文档列表，每个元素包含 id/content/metadata/score
        top_k: 精排后保留的数量
    
    返回：
        重排序后的文档列表（取 Top-K）
    """
    if not candidates:
        return []
    
    try:
        reranker = get_reranker()
        # 构造 [query, document] 对
        pairs = [[query, doc["content"]] for doc in candidates]
        scores = reranker.compute_score(pairs)
        
        # 如果只返回一个分数，包装为列表
        if isinstance(scores, (int, float)):
            scores = [scores]
        
        # 复制候选字典后附加精排分数，避免污染被检索缓存复用的原始候选。
        scored = []
        for doc, score in zip(candidates, scores):
            new_doc = dict(doc)
            new_doc["rerank_score"] = float(score)
            scored.append(new_doc)
        
        # 按 rerank_score 降序排序，取 top_k
        scored.sort(key=lambda x: x["rerank_score"], reverse=True)
        return scored[:top_k]
        
    except Exception as e:
        logger.warning(f"Reranker failed, falling back to original order: {e}")
        # 降级：Reranker 失败时返回原始顺序的前 top_k 个
        return candidates[:top_k]
