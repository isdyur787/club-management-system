import chromadb
import logging
import threading
from typing import List, Dict, Optional
from config import CHROMA_PERSIST_DIR
from store.vector_store import VectorStoreRepository, SearchResult

logger = logging.getLogger(__name__)

_client = None
_client_lock = threading.Lock()

def chroma_where(where: Dict) -> Dict:
    if len(where) > 1:
        return {"$and": [{key: value} for key, value in where.items()]}
    return where

def get_chroma_client():
    global _client
    if _client is None:
        with _client_lock:
            if _client is None:  # 双重检查锁定
                _client = chromadb.PersistentClient(path=CHROMA_PERSIST_DIR)
    return _client

class ChromaVectorStore(VectorStoreRepository):
    def __init__(self):
        self.client = get_chroma_client()

    def _get_collection(self, collection: str):
        return self.client.get_or_create_collection(
            name=collection,
            metadata={"hnsw:space": "cosine"}
        )

    def add_documents(self, collection: str, documents: List[str],
                      metadatas: List[Dict], ids: List[str]) -> None:
        col = self._get_collection(collection)
        from rag.ingestion import get_embedding_model
        model = get_embedding_model()
        embeddings = model.encode(documents).tolist()
        col.upsert(documents=documents, embeddings=embeddings,
                metadatas=metadatas, ids=ids)
        logger.info(f"Added {len(documents)} documents to {collection}")

    def search(self, collection: str, query_embedding: List[float],
               top_k: int = 20, where: Optional[Dict] = None) -> List[SearchResult]:
        col = self._get_collection(collection)
        kwargs = {"query_embeddings": [query_embedding], "n_results": min(top_k, col.count() or 1)}
        if where:
            kwargs["where"] = chroma_where(where)
        try:
            results = col.query(**kwargs)
        except Exception as e:
            logger.warning(f"Search failed: {e}")
            return []

        search_results = []
        if results and results["ids"] and results["ids"][0]:
            for i, id in enumerate(results["ids"][0]):
                search_results.append(SearchResult(
                    id=id,
                    content=results["documents"][0][i] if results["documents"] else "",
                    metadata=results["metadatas"][0][i] if results["metadatas"] else {},
                    score=1.0 - (results["distances"][0][i] if results["distances"] else 0)
                ))
        return search_results

    def delete_documents(self, collection: str, ids: List[str]) -> None:
        col = self._get_collection(collection)
        col.delete(ids=ids)
        logger.info(f"Deleted {len(ids)} documents from {collection}")

    def delete_collection(self, collection: str) -> None:
        try:
            self.client.delete_collection(collection)
        except Exception as e:
            logger.warning(f"Delete collection failed: {e}")

    def count(self, collection: str) -> int:
        try:
            col = self._get_collection(collection)
            return col.count()
        except Exception:
            return 0
