import asyncio
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from routes import knowledge
from rag import retrieval


class KnowledgeLifecycleTests(unittest.TestCase):
    @patch("routes.knowledge.DBReader")
    @patch("routes.knowledge.add_document", return_value=("vector-doc", 2))
    def test_upload_persists_all_chunk_ids_for_future_deletion(self, add, db):
        cursor = db.return_value.__enter__.return_value.conn.cursor.return_value.__enter__.return_value
        cursor.lastrowid = 51
        with tempfile.NamedTemporaryFile(suffix=".md", mode="w", delete=False) as f:
            f.write("## 章程\n活动提前两周申请。")
        try:
            result = asyncio.run(knowledge.ingest_document({
                "club_id": 1, "doc_type": "charter", "title": "章程", "file_path": f.name
            }))
        finally:
            os.unlink(f.name)
        self.assertEqual(result["id"], 51)
        insert = next(c for c in cursor.execute.call_args_list if "INSERT" in c.args[0])
        self.assertIn("chroma_ids", insert.args[0])
        self.assertTrue(any(isinstance(p, str) and "vector-doc_chunk_0" in p for p in insert.args[1]))

    @patch("routes.knowledge.DBReader")
    @patch("routes.knowledge.delete_document")
    def test_delete_removes_stored_vector_chunks(self, delete, db):
        cursor = db.return_value.__enter__.return_value.conn.cursor.return_value.__enter__.return_value
        cursor.fetchone.return_value = {"id": 51, "club_id": 1, "chroma_ids": json.dumps(["vector-doc_chunk_0", "vector-doc_chunk_1"])}
        asyncio.run(knowledge.delete_document_endpoint(51))
        delete.assert_called_once_with(1, ["vector-doc_chunk_0", "vector-doc_chunk_1"])

    @patch("rag.retrieval.ChromaVectorStore")
    def test_bm25_cache_keeps_filters_separate(self, store):
        retrieval._bm25_cache.clear()
        col = store.return_value._get_collection.return_value
        col.get.side_effect = [
            {"ids": ["a"], "documents": ["收获 团队"], "metadatas": [{"section_type": "reflections"}]},
            {"ids": ["b"], "documents": ["问题 场地"], "metadatas": [{"section_type": "problems"}]},
        ]
        retrieval._get_bm25_index("club_1", {"section_type": "reflections"})
        other = retrieval._get_bm25_index("club_1", {"section_type": "problems"})
        self.assertEqual(other["ids"], ["b"])


if __name__ == "__main__":
    unittest.main()
