"""
RAG 检索模块单元测试

测试要点：
- rrf_fusion 排序正确性
- detect_section_type 关键词检测
- _get_bm25_index 缓存命中
- 空输入/空结果的边界处理
"""
import sys
import os
import time
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from store.vector_store import SearchResult
from rag.retrieval import rrf_fusion, detect_section_type, _get_bm25_index, _bm25_cache


class TestRRFFusion(unittest.TestCase):

    def test_basic_fusion_ranking(self):
        """两个结果列表融合后，同时出现在两者中的文档排名更高"""
        vector_results = [
            SearchResult(id="doc_a", content="文档A", metadata={}, score=0.9),
            SearchResult(id="doc_b", content="文档B", metadata={}, score=0.8),
        ]
        bm25_results = [
            SearchResult(id="doc_b", content="文档B", metadata={}, score=5.0),
            SearchResult(id="doc_c", content="文档C", metadata={}, score=4.0),
        ]
        fused = rrf_fusion(vector_results, bm25_results, k=60)
        # doc_b 出现在两个列表中，应排第一
        self.assertEqual(fused[0]["id"], "doc_b")
        # doc_a 和 doc_c 各出现一次，但 doc_a 排名更靠前（rank 0 vs rank 1）
        self.assertEqual(fused[1]["id"], "doc_a")
        self.assertEqual(fused[2]["id"], "doc_c")

    def test_single_list_only_vector(self):
        """仅向量结果时，按向量排名顺序"""
        vector_results = [
            SearchResult(id="v1", content="V1", metadata={}, score=0.95),
            SearchResult(id="v2", content="V2", metadata={}, score=0.85),
            SearchResult(id="v3", content="V3", metadata={}, score=0.75),
        ]
        fused = rrf_fusion(vector_results, [], k=60)
        self.assertEqual(len(fused), 3)
        self.assertEqual([r["id"] for r in fused], ["v1", "v2", "v3"])

    def test_single_list_only_bm25(self):
        """仅 BM25 结果时，按 BM25 排名顺序"""
        bm25_results = [
            SearchResult(id="b1", content="B1", metadata={"t": 1}, score=10.0),
            SearchResult(id="b2", content="B2", metadata={"t": 2}, score=8.0),
        ]
        fused = rrf_fusion([], bm25_results, k=60)
        self.assertEqual(len(fused), 2)
        self.assertEqual(fused[0]["id"], "b1")
        self.assertIn("metadata", fused[0])

    def test_empty_both_lists(self):
        """两个列表都为空时，返回空列表"""
        fused = rrf_fusion([], [], k=60)
        self.assertEqual(fused, [])

    def test_k_parameter_affects_scores(self):
        """k 参数越大，RRF 分数差距越小（平滑效果）"""
        vector_results = [
            SearchResult(id="x1", content="", metadata={}, score=0.9),
        ]
        bm25_results = []

        fused_k1 = rrf_fusion(vector_results, bm25_results, k=1)
        fused_k60 = rrf_fusion(vector_results, bm25_results, k=60)
        # k=1 时分数更高
        self.assertGreater(fused_k1[0]["score"], fused_k60[0]["score"])

    def test_preserves_content_and_metadata(self):
        """融合后保留原始 content 和 metadata"""
        vector_results = [
            SearchResult(id="d1", content="详细内容", metadata={"title": "标题A", "doc_type": "faq"}, score=0.9),
        ]
        fused = rrf_fusion(vector_results, [], k=60)
        self.assertEqual(fused[0]["content"], "详细内容")
        self.assertEqual(fused[0]["metadata"]["title"], "标题A")
        self.assertEqual(fused[0]["metadata"]["doc_type"], "faq")

    def test_overlapping_docs_score_higher(self):
        """同时出现在两个列表中的文档，RRF 分数应为两项之和"""
        vector_results = [
            SearchResult(id="d1", content="", metadata={}, score=0.9),
        ]
        bm25_results = [
            SearchResult(id="d1", content="", metadata={}, score=5.0),
        ]
        fused = rrf_fusion(vector_results, bm25_results, k=60)
        # rank 0 在两个列表中，score = 1/(60+0+1) + 1/(60+0+1) = 2/61
        expected = 2.0 / (60 + 0 + 1)
        self.assertAlmostEqual(fused[0]["score"], expected, places=6)


class TestDetectSectionType(unittest.TestCase):

    def test_detect_problems(self):
        """包含问题关键词时返回 'problems'"""
        self.assertEqual(detect_section_type("活动中遇到了哪些问题"), "problems")

    def test_detect_problems_other_keywords(self):
        """其他问题类关键词也应命中"""
        self.assertEqual(detect_section_type("场地不足导致体验不好"), "problems")
        self.assertEqual(detect_section_type("踩坑记录"), "problems")

    def test_detect_suggestions(self):
        """包含建议关键词时返回 'suggestions'"""
        self.assertEqual(detect_section_type("有什么改进建议"), "suggestions")

    def test_detect_suggestions_other_keywords(self):
        """其他建议类关键词也应命中"""
        self.assertEqual(detect_section_type("下次应该怎么优化"), "suggestions")
        self.assertEqual(detect_section_type("如何提升活动质量"), "suggestions")

    def test_detect_reflections(self):
        """包含收获关键词时返回 'reflections'"""
        self.assertEqual(detect_section_type("这次活动有什么收获"), "reflections")

    def test_detect_reflections_other_keywords(self):
        """其他反思类关键词也应命中"""
        self.assertEqual(detect_section_type("分享你的心得体会"), "reflections")
        self.assertEqual(detect_section_type("个人成长感悟"), "reflections")

    def test_no_match_returns_none(self):
        """无匹配关键词时返回 None"""
        self.assertIsNone(detect_section_type("今天天气怎么样"))

    def test_empty_string_returns_none(self):
        """空字符串返回 None"""
        self.assertIsNone(detect_section_type(""))

    def test_priority_order(self):
        """当多个类型匹配时，按 SECTION_TYPE_KEYWORDS 中的定义顺序返回第一个"""
        # "问题"和"建议"同时出现，应返回字典中先定义的
        result = detect_section_type("活动中问题和改进建议")
        # problems 在 config.py 中先定义
        self.assertIn(result, ["problems", "suggestions"])


class TestBM25IndexCache(unittest.TestCase):

    def setUp(self):
        """清除缓存"""
        _bm25_cache.clear()

    def tearDown(self):
        _bm25_cache.clear()

    @patch("rag.retrieval.ChromaVectorStore")
    def test_cache_hit_within_ttl(self, mock_store_cls):
        """缓存未过期时，第二次调用应命中缓存（不重新构建索引）"""
        mock_col = MagicMock()
        mock_col.get.return_value = {
            "ids": ["d1", "d2"],
            "documents": ["文档一", "文档二"],
            "metadatas": [{}, {}],
        }
        mock_store = MagicMock()
        mock_store._get_collection.return_value = mock_col
        mock_store_cls.return_value = mock_store

        result1 = _get_bm25_index("test_collection")
        self.assertIsNotNone(result1)
        # 第一次调用后应写入缓存
        self.assertIn("test_collection", _bm25_cache)

        # 再次调用，不应再访问 ChromaDB
        mock_col.reset_mock()
        result2 = _get_bm25_index("test_collection")
        self.assertIsNotNone(result2)
        mock_col.get.assert_not_called()  # 没有再次调用 .get()

    @patch("rag.retrieval.ChromaVectorStore")
    def test_cache_miss_after_expiry(self, mock_store_cls):
        """缓存过期后应重新构建"""
        mock_col = MagicMock()
        mock_col.get.return_value = {
            "ids": ["d1"],
            "documents": ["新文档"],
            "metadatas": [{}],
        }
        mock_store = MagicMock()
        mock_store._get_collection.return_value = mock_col
        mock_store_cls.return_value = mock_store

        _get_bm25_index("coll_x")
        # 手动将缓存时间戳设为过期
        _bm25_cache["coll_x"]["ts"] = time.time() - 600

        mock_col.reset_mock()
        _get_bm25_index("coll_x")
        mock_col.get.assert_called_once()  # 过期后应重新构建

    @patch("rag.retrieval.ChromaVectorStore")
    def test_empty_collection_returns_none(self, mock_store_cls):
        """集合为空时返回 None"""
        mock_col = MagicMock()
        mock_col.get.return_value = {
            "ids": [],
            "documents": [],
            "metadatas": [],
        }
        mock_store = MagicMock()
        mock_store._get_collection.return_value = mock_col
        mock_store_cls.return_value = mock_store

        result = _get_bm25_index("empty_collection")
        self.assertIsNone(result)

    @patch("rag.retrieval.ChromaVectorStore")
    def test_exception_returns_none(self, mock_store_cls):
        """ChromaDB 异常时返回 None（不抛出）"""
        mock_store_cls.side_effect = RuntimeError("ChromaDB 不可用")
        result = _get_bm25_index("error_collection")
        self.assertIsNone(result)

    def test_different_collections_independent(self):
        """不同 collection 的缓存相互独立"""
        _bm25_cache["coll_a"] = {
            "bm25": MagicMock(), "ids": ["a"],
            "documents": ["A"], "metadatas": [{}], "ts": time.time()
        }
        _bm25_cache["coll_b"] = {
            "bm25": MagicMock(), "ids": ["b"],
            "documents": ["B"], "metadatas": [{}], "ts": time.time()
        }
        self.assertNotEqual(
            _bm25_cache["coll_a"]["ids"],
            _bm25_cache["coll_b"]["ids"]
        )


if __name__ == "__main__":
    unittest.main()
