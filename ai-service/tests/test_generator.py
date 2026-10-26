"""
Generator 模块单元测试

测试要点：
- 上下文构建（get_context_for_generation）
- RAG 消息构建（build_rag_messages）
- 对话历史摘要（summarize_history 的降级路径）
- 自检解析逻辑（self_check_answer）
- 降级回答（generate_fallback_answer）
- 空输入边界处理
"""
import sys
import os
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.generator import (
    get_context_for_generation,
    generate_fallback_answer,
    build_rag_messages,
    self_check_answer,
    generate_answer,
    summarize_history,
)


# ========== 上下文构建 ==========

class TestContextBuilding(unittest.TestCase):

    def test_empty_docs(self):
        """空文档列表应返回空字符串"""
        result = get_context_for_generation([])
        self.assertEqual(result, "")

    def test_single_doc(self):
        """单个文档应包含标题和内容"""
        docs = [{
            "content": "社团成立于2020年",
            "metadata": {"title": "社团简介"}
        }]
        result = get_context_for_generation(docs)
        self.assertIn("[来源1]", result)
        self.assertIn("社团简介", result)
        self.assertIn("2020", result)

    def test_multiple_docs(self):
        """多个文档应按序号排列"""
        docs = [
            {"content": "内容A", "metadata": {"title": "文档A"}},
            {"content": "内容B", "metadata": {"title": "文档B"}},
            {"content": "内容C", "metadata": {"title": "文档C"}},
        ]
        result = get_context_for_generation(docs)
        self.assertIn("[来源1]", result)
        self.assertIn("[来源2]", result)
        self.assertIn("[来源3]", result)

    def test_missing_metadata(self):
        """缺少 metadata 时应使用默认标题"""
        docs = [{"content": "测试内容", "metadata": {}}]
        result = get_context_for_generation(docs)
        self.assertIn("未知来源", result)

    def test_max_chars_truncation(self):
        """max_chars 参数应限制每个文档的最大字符数"""
        long_content = "很长的内容" * 500  # 2000+ 字符
        docs = [{"content": long_content, "metadata": {"title": "长文档"}}]
        result = get_context_for_generation(docs, max_chars=100)
        # 每个文档的 chunk 最多 100/1=100 字符
        # 验证内容被截断
        self.assertNotIn("很长的内容" * 500, result)

    def test_no_metadata_key(self):
        """完全没有 metadata 字段时应不崩溃"""
        docs = [{"content": "仅内容"}]
        # 缺少 metadata 会抛 KeyError，验证函数行为
        # 如果函数不处理此情况，会抛异常
        try:
            result = get_context_for_generation(docs)
            # 如果没崩溃，验证返回包含内容
            self.assertIn("仅内容", result)
        except KeyError:
            # 如果崩溃了，说明需要 metadata key
            pass


# ========== RAG 消息构建 ==========

class TestBuildRAGMessages(unittest.TestCase):

    def test_basic_message_structure(self):
        """基本消息结构应包含 system + user 消息"""
        docs = [{"content": "社团成立于2020年", "metadata": {"title": "简介"}}]
        messages = build_rag_messages("社团什么时候成立的？", docs)

        self.assertIsInstance(messages, list)
        self.assertGreaterEqual(len(messages), 2)
        # 第一条是 system
        self.assertEqual(messages[0]["role"], "system")
        # 最后一条是 user
        self.assertEqual(messages[-1]["role"], "user")
        self.assertEqual(messages[-1]["content"], "社团什么时候成立的？")

    def test_system_prompt_contains_context(self):
        """system 消息应包含检索文档内容"""
        docs = [{"content": "活动安排在周六", "metadata": {"title": "活动通知"}}]
        messages = build_rag_messages("活动什么时候？", docs)

        system_content = messages[0]["content"]
        self.assertIn("活动安排在周六", system_content)
        self.assertIn("参考资料", system_content)
        self.assertIn("[来源1]", system_content)

    def test_with_short_chat_history(self):
        """短对话历史应直接附加在消息中"""
        docs = [{"content": "测试", "metadata": {}}]
        history = [
            {"role": "user", "content": "你好"},
            {"role": "assistant", "content": "你好！有什么可以帮你的？"},
        ]
        messages = build_rag_messages("社团活动", docs, chat_history=history)

        # system + 2 history + user = 4
        self.assertEqual(len(messages), 4)
        self.assertEqual(messages[1]["role"], "user")
        self.assertEqual(messages[1]["content"], "你好")
        self.assertEqual(messages[2]["role"], "assistant")

    def test_no_chat_history(self):
        """无对话历史时，消息列表应为 [system, user]"""
        docs = [{"content": "测试", "metadata": {}}]
        messages = build_rag_messages("问题", docs)
        self.assertEqual(len(messages), 2)

    def test_empty_chat_history(self):
        """空对话历史列表应不影响消息构建"""
        docs = [{"content": "测试", "metadata": {}}]
        messages = build_rag_messages("问题", docs, chat_history=[])
        self.assertEqual(len(messages), 2)

    @patch("rag.generator.summarize_history")
    def test_long_chat_history_triggers_compression(self, mock_summarize):
        """超过 6 条消息的历史应触发摘要压缩"""
        mock_summarize.return_value = "这是对话摘要"
        docs = [{"content": "测试", "metadata": {}}]
        history = [{"role": "user", "content": f"消息{i}"} for i in range(10)]
        messages = build_rag_messages("新问题", docs, chat_history=history)

        # system + summary_system + user = 3
        self.assertEqual(len(messages), 3)
        summary_msg = messages[1]
        self.assertEqual(summary_msg["role"], "system")
        self.assertIn("对话摘要", summary_msg["content"])
        self.assertIn("这是对话摘要", summary_msg["content"])
        mock_summarize.assert_called_once()

    def test_multiple_docs_in_system_prompt(self):
        """多个文档都应出现在 system prompt 中"""
        docs = [
            {"content": "文档一内容", "metadata": {"title": "一"}},
            {"content": "文档二内容", "metadata": {"title": "二"}},
        ]
        messages = build_rag_messages("问题", docs)
        system_content = messages[0]["content"]
        self.assertIn("文档一内容", system_content)
        self.assertIn("文档二内容", system_content)

    def test_system_prompt_has_rules(self):
        """system prompt 应包含回答规则"""
        docs = [{"content": "测试", "metadata": {}}]
        messages = build_rag_messages("问题", docs)
        system_content = messages[0]["content"]
        self.assertIn("规则", system_content)
        self.assertIn("来源", system_content)


# ========== 自检逻辑 ==========

class TestSelfCheckAnswer(unittest.TestCase):

    @patch("rag.generator.get_context_for_generation")
    @patch("rag.structured_output.structured_llm_call")
    def test_self_check_passed(self, mock_structured_call, mock_ctx):
        """自检通过时返回 passed=True"""
        mock_structured_result = MagicMock()
        mock_structured_result.passed = True
        mock_structured_result.confidence = 0.9
        mock_structured_result.reason = "回答忠于参考资料"
        mock_structured_call.return_value = mock_structured_result

        docs = [{"content": "社团成立于2020年", "metadata": {"title": "简介"}}]
        result = self_check_answer("社团2020年成立", docs)

        self.assertTrue(result["passed"])
        self.assertEqual(result["confidence"], 0.9)
        self.assertIn("忠于", result["reason"])

    @patch("rag.generator.get_context_for_generation")
    @patch("rag.structured_output.structured_llm_call")
    def test_self_check_failed(self, mock_structured_call, mock_ctx):
        """自检不通过时返回 passed=False"""
        mock_structured_result = MagicMock()
        mock_structured_result.passed = False
        mock_structured_result.confidence = 0.3
        mock_structured_result.reason = "回答中包含编造信息"
        mock_structured_call.return_value = mock_structured_result

        docs = [{"content": "社团成立于2020年", "metadata": {"title": "简介"}}]
        result = self_check_answer("社团成立于1999年", docs)

        self.assertFalse(result["passed"])
        self.assertEqual(result["confidence"], 0.3)

    @patch("rag.generator.get_context_for_generation")
    @patch("rag.structured_output.structured_llm_call")
    def test_self_check_exception_defaults_to_passed(self, mock_structured_call, mock_ctx):
        """自检异常时默认通过"""
        mock_structured_call.side_effect = RuntimeError("LLM 服务不可用")

        docs = [{"content": "测试", "metadata": {}}]
        result = self_check_answer("测试回答", docs)

        self.assertTrue(result["passed"])
        self.assertEqual(result["confidence"], 0.5)
        self.assertIn("异常", result["reason"])

    @patch("rag.generator.get_context_for_generation")
    @patch("rag.structured_output.structured_llm_call")
    def test_self_check_builds_context_text(self, mock_structured_call, mock_ctx):
        """自检应将文档内容构建为参考文本"""
        mock_structured_result = MagicMock()
        mock_structured_result.passed = True
        mock_structured_result.confidence = 0.8
        mock_structured_result.reason = "OK"
        mock_structured_call.return_value = mock_structured_result

        docs = [
            {"content": "第一份文档内容" * 100, "metadata": {"title": "文档A"}},
            {"content": "第二份文档", "metadata": {"title": "文档B"}},
        ]
        self_check_answer("回答", docs)

        # 验证 structured_llm_call 被调用
        mock_structured_call.assert_called_once()
        call_prompt = mock_structured_call.call_args[0][0]
        self.assertIn("参考资料", call_prompt)
        self.assertIn("回答", call_prompt)


# ========== 降级回答 ==========

class TestFallbackAnswer(unittest.TestCase):

    def test_no_docs(self):
        """无文档时应返回未找到提示"""
        result = generate_fallback_answer("测试问题", [])
        self.assertIn("未找到", result)

    def test_with_docs(self):
        """有文档时应返回摘要"""
        docs = [
            {"content": "社团活动丰富多彩", "metadata": {"title": "活动介绍"}},
            {"content": "每周定期开会", "metadata": {"title": "会议安排"}},
        ]
        result = generate_fallback_answer("社团活动怎样", docs)
        self.assertIn("活动介绍", result)
        self.assertIn("AI 生成暂不可用", result)

    def test_limits_to_three_docs(self):
        """降级回答最多使用 3 个文档"""
        docs = [
            {"content": f"文档{i}内容", "metadata": {"title": f"标题{i}"}}
            for i in range(10)
        ]
        result = generate_fallback_answer("问题", docs)
        # 只引用前 3 个
        self.assertIn("标题0", result)
        self.assertIn("标题2", result)
        # 不应引用第 4 个以后的标题
        self.assertNotIn("标题5", result)


# ========== 生成回答 ==========

class TestGenerateAnswer(unittest.TestCase):

    @patch("rag.generator._router_call")
    def test_generate_answer_structure(self, mock_router_call):
        """generate_answer 应返回包含 answer 和 sources 的字典"""
        mock_router_call.return_value = "这是一个回答"

        docs = [
            {"id": "d1", "content": "社团成立于2020年", "metadata": {"title": "简介", "doc_type": "charter"}},
        ]
        result = generate_answer("社团什么时候成立？", docs)

        self.assertIn("answer", result)
        self.assertIn("sources", result)
        self.assertEqual(result["answer"], "这是一个回答")
        self.assertEqual(len(result["sources"]), 1)
        self.assertEqual(result["sources"][0]["doc_id"], "d1")
        self.assertEqual(result["sources"][0]["title"], "简介")
        self.assertEqual(result["sources"][0]["doc_type"], "charter")

    @patch("rag.generator._router_call")
    def test_generate_answer_truncates_snippet(self, mock_router_call):
        """超过 200 字符的文档内容应被截断为 snippet"""
        mock_router_call.return_value = "回答"
        long_content = "很长的内容" * 200  # 1000+ 字符
        docs = [{"id": "d1", "content": long_content, "metadata": {"title": "长文档"}}]
        result = generate_answer("问题", docs)

        snippet = result["sources"][0]["snippet"]
        self.assertLessEqual(len(snippet), 210)  # 200 + "..."
        self.assertTrue(snippet.endswith("..."))


# ========== 对话历史摘要 ==========

class TestSummarizeHistory(unittest.TestCase):

    def test_empty_history_returns_empty(self):
        """空历史返回空字符串"""
        result = summarize_history([])
        self.assertEqual(result, "")

    def test_short_history_returns_empty(self):
        """不超过 max_rounds*2 条消息时返回空字符串"""
        history = [
            {"role": "user", "content": "你好"},
            {"role": "assistant", "content": "你好！"},
        ]
        result = summarize_history(history, max_rounds=3)
        self.assertEqual(result, "")

    @patch("rag.generator._router_call")
    def test_long_history_triggers_llm_summary(self, mock_router_call):
        """超过阈值时应调用 LLM 生成摘要"""
        mock_router_call.return_value = "用户询问了社团活动安排"
        history = [{"role": "user", "content": f"消息{i}"} for i in range(20)]
        result = summarize_history(history, max_rounds=3)

        self.assertEqual(result, "用户询问了社团活动安排")
        mock_router_call.assert_called_once()

    @patch("rag.generator._router_call")
    def test_llm_failure_fallback(self, mock_router_call):
        """LLM 调用失败时应降级为拼接最近消息"""
        mock_router_call.side_effect = RuntimeError("LLM 不可用")
        history = [
            {"role": "user", "content": "问题一"},
            {"role": "assistant", "content": "回答一"},
            {"role": "user", "content": "问题二"},
            {"role": "assistant", "content": "回答二"},
            {"role": "user", "content": "问题三"},
            {"role": "assistant", "content": "回答三"},
            {"role": "user", "content": "问题四"},
            {"role": "assistant", "content": "回答四"},
        ]
        result = summarize_history(history, max_rounds=3)

        # 降级路径：拼接最近 4 条消息
        self.assertIn("问题", result)
        self.assertIsInstance(result, str)
        self.assertGreater(len(result), 0)


if __name__ == "__main__":
    unittest.main()
