import uuid
import logging
from typing import List, Dict
from store.chroma_store import ChromaVectorStore
from rag.ingestion import ingest_document

logger = logging.getLogger(__name__)

def invalidate_knowledge_cache(club_id: int):
    from rag.latency import get_retrieval_cache
    from rag.retrieval import _bm25_cache, _bm25_lock
    get_retrieval_cache().clear()
    with _bm25_lock:
        _bm25_cache.clear()
    store = ChromaVectorStore()
    cache = store._get_collection("semantic_cache")
    cache.delete(where={"club_id": club_id})

def get_collection_name(club_id: int) -> str:
    return f"club_{club_id}"

def add_document(club_id: int, doc_type: str, title: str,
                 content: str, source: str, source_id: int = None) -> tuple:
    collection = get_collection_name(club_id)
    doc_id = str(uuid.uuid4())
    metadata = {
        "club_id": club_id,
        "doc_type": doc_type,
        "title": title,
        "source": source,
    }
    if source_id:
        metadata["source_id"] = source_id

    chunk_count = ingest_document(collection, doc_id, content, metadata)
    invalidate_knowledge_cache(club_id)
    return doc_id, chunk_count

def delete_document(club_id: int, chroma_ids: List[str]) -> None:
    collection = get_collection_name(club_id)
    store = ChromaVectorStore()
    if chroma_ids:
        store.delete_documents(collection, chroma_ids)
    invalidate_knowledge_cache(club_id)

def count_documents(club_id: int) -> int:
    collection = get_collection_name(club_id)
    store = ChromaVectorStore()
    return store.count(collection)
