from abc import ABC, abstractmethod
from typing import List, Dict, Optional
from dataclasses import dataclass

@dataclass
class SearchResult:
    id: str
    content: str
    metadata: Dict
    score: float

class VectorStoreRepository(ABC):
    @abstractmethod
    def add_documents(self, collection: str, documents: List[str],
                      metadatas: List[Dict], ids: List[str]) -> None:
        pass

    @abstractmethod
    def search(self, collection: str, query_embedding: List[float],
               top_k: int = 20, where: Optional[Dict] = None) -> List[SearchResult]:
        pass

    @abstractmethod
    def delete_documents(self, collection: str, ids: List[str]) -> None:
        pass

    @abstractmethod
    def delete_collection(self, collection: str) -> None:
        pass

    @abstractmethod
    def count(self, collection: str) -> int:
        pass
