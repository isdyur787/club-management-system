"""
Reranker 模块单元测试

测试要点：
- rerank 返回新列表（不修改输入）
- 空 candidates 处理
- 降级路径（reranker 不可用时返回原始排序）
"""
import sys
import os
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.reranker import rerank


class TestRerank(unittest.TestCase):

    def _make_candidates(self, n=3):
        """生成测试候选文档"""
        return [
            {"id": f"doc_{i}", "content": f"文档内容{i}", "metadata": {"title": f"标题{i}"}, "score": 0.9 - i * 0.1}
            for i in range(n)
        ]

    @patch("rag.reranker.get_reranker")
    def test_returns_new_list_not_mutate_input(self, mock_get_reranker):
        """rerank 应返回新列表，不修改原始输入"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.3, 0.9, 0.6]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(3)
        original_ids = [c["id"] for c in candidates]
        original_contents = [c["content"] for c in candidates]

        result = rerank("测试查询", candidates, top_k=3)

        # 原始列表未被修改
        self.assertEqual([c["id"] for c in candidates], original_ids)
        self.assertEqual([c["content"] for c in candidates], original_contents)
        # 返回新列表
        self.assertIsNot(result, candidates)

    @patch("rag.reranker.get_reranker")
    def test_ranking_by_rerank_score(self, mock_get_reranker):
        """结果应按 rerank_score 降序排列"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.1, 0.9, 0.5]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(3)
        result = rerank("测试查询", candidates, top_k=3)

        scores = [r["rerank_score"] for r in result]
        self.assertEqual(scores, sorted(scores, reverse=True))
        # 最高分应该是 doc_1（原始 index 1，compute_score 返回 0.9）
        self.assertEqual(result[0]["id"], "doc_1")

    @patch("rag.reranker.get_reranker")
    def test_top_k_limits_results(self, mock_get_reranker):
        """top_k 参数应限制返回数量"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.9, 0.8, 0.7, 0.6, 0.5]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(5)
        result = rerank("测试查询", candidates, top_k=2)

        self.assertEqual(len(result), 2)

    @patch("rag.reranker.get_reranker")
    def test_top_k_larger_than_candidates(self, mock_get_reranker):
        """top_k 大于候选数量时，返回全部候选"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.9, 0.8]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(2)
        result = rerank("测试查询", candidates, top_k=10)

        self.assertEqual(len(result), 2)

    @patch("rag.reranker.get_reranker")
    def test_single_candidate(self, mock_get_reranker):
        """单个候选文档时正常处理（compute_score 返回标量）"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = 0.85  # 标量，非列表
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(1)
        result = rerank("测试查询", candidates, top_k=3)

        self.assertEqual(len(result), 1)
        self.assertAlmostEqual(result[0]["rerank_score"], 0.85)

    def test_empty_candidates_returns_empty(self):
        """空候选列表应返回空列表"""
        result = rerank("任何查询", [], top_k=3)
        self.assertEqual(result, [])

    @patch("rag.reranker.get_reranker")
    def test_fallback_on_reranker_failure(self, mock_get_reranker):
        """reranker 不可用时应降级，返回原始顺序的前 top_k 个"""
        mock_get_reranker.side_effect = RuntimeError("模型加载失败")

        candidates = self._make_candidates(5)
        result = rerank("测试查询", candidates, top_k=3)

        # 降级路径：返回原始顺序的前 top_k
        self.assertEqual(len(result), 3)
        self.assertEqual(result[0]["id"], "doc_0")
        self.assertEqual(result[1]["id"], "doc_1")
        self.assertEqual(result[2]["id"], "doc_2")
        # 降级路径不添加 rerank_score
        self.assertNotIn("rerank_score", result[0])

    @patch("rag.reranker.get_reranker")
    def test_fallback_preserves_original_order(self, mock_get_reranker):
        """降级时应保持 candidates 的原始顺序"""
        mock_get_reranker.side_effect = RuntimeError("GPU 不可用")

        candidates = [
            {"id": "z_doc", "content": "Z", "metadata": {}, "score": 0.1},
            {"id": "a_doc", "content": "A", "metadata": {}, "score": 0.9},
            {"id": "m_doc", "content": "M", "metadata": {}, "score": 0.5},
        ]
        result = rerank("查询", candidates, top_k=2)

        self.assertEqual(result[0]["id"], "z_doc")
        self.assertEqual(result[1]["id"], "a_doc")

    @patch("rag.reranker.get_reranker")
    def test_fallback_on_compute_score_error(self, mock_get_reranker):
        """compute_score 内部异常时也应降级"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.side_effect = ValueError("输入格式错误")
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(3)
        result = rerank("查询", candidates, top_k=2)

        # 降级路径
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["id"], "doc_0")

    @patch("rag.reranker.get_reranker")
    def test_rerank_score_added_to_new_docs(self, mock_get_reranker):
        """正常路径下，rerank_score 应被添加到新文档字典中"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.1, 0.5]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(2)
        result = rerank("查询", candidates, top_k=3)

        for doc in result:
            self.assertIn("rerank_score", doc)
            self.assertIsInstance(doc["rerank_score"], float)

    @patch("rag.reranker.get_reranker")
    def test_original_candidates_not_have_rerank_score(self, mock_get_reranker):
        """原始 candidates 不应被添加 rerank_score"""
        mock_reranker = MagicMock()
        mock_reranker.compute_score.return_value = [0.1, 0.5]
        mock_get_reranker.return_value = mock_reranker

        candidates = self._make_candidates(2)
        rerank("查询", candidates, top_k=3)

        for doc in candidates:
            self.assertNotIn("rerank_score", doc)


if __name__ == "__main__":
    unittest.main()
