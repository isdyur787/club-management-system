import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from routes.chat_nodes import self_check_node


class ChatFailureTests(unittest.TestCase):
    def test_failed_generation_is_not_eligible_for_answer_cache(self):
        result = self_check_node({
            "answer": "以下为知识库中的相关内容（AI 生成暂不可用，仅供参考）",
            "fine_results": [{"content": "社团资料"}],
            "error": "Connection error",
        })
        self.assertFalse(result["check_passed"])

    def test_general_answer_without_documents_can_pass(self):
        result = self_check_node({"answer": "社团活动建议", "fine_results": [], "error": None})
        self.assertTrue(result["check_passed"])
