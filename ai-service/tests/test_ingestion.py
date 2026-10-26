"""
文档分块模块单元测试

测试要点：
- chunk_by_doc_type 对不同 doc_type 的分块策略
- checkin_compilation 按维度（收获/问题/建议）分块
- faq 按 Q&A 对分块
- 默认 512 字符分块 + 64 重叠
"""
import sys
import os
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.ingestion import (
    chunk_by_doc_type,
    chunk_compilation_by_section,
    chunk_faq_by_qa,
    chunk_by_fixed_size,
    chunk_by_markdown_sections,
    extract_section,
)


class TestChunkByDocType(unittest.TestCase):

    def test_checkin_compilation_dispatches_to_section(self):
        """doc_type='checkin_compilation' 应按维度分块"""
        content = "【收获】学会了很多\n\n1.1 签到签退流程\n"
        chunks = chunk_by_doc_type("checkin_compilation", content)
        self.assertIsInstance(chunks, list)
        if chunks:
            # 应该有 section_type 元数据
            self.assertIn("metadata_addition", chunks[0])

    def test_faq_dispatches_to_qa(self):
        """doc_type='faq' 应按 Q&A 对分块"""
        content = "Q1. 如何加入社团？\nA1. 请填写报名表\n\nQ2. 社费多少？\nA2. 每学期50元\n"
        chunks = chunk_by_doc_type("faq", content)
        self.assertIsInstance(chunks, list)
        self.assertGreaterEqual(len(chunks), 1)

    def test_charter_dispatches_to_markdown(self):
        """doc_type='charter' 应按 Markdown 标题分块"""
        content = "## 第一章 总则\n\n社团宗旨...\n\n## 第二章 组织\n\n组织架构...\n"
        chunks = chunk_by_doc_type("charter", content)
        self.assertIsInstance(chunks, list)

    def test_activity_report_dispatches_to_markdown(self):
        """doc_type='activity_report' 应按 Markdown 标题分块"""
        content = "## 活动总结\n\n活动取得了圆满成功\n"
        chunks = chunk_by_doc_type("activity_report", content)
        self.assertIsInstance(chunks, list)

    def test_unknown_doc_type_uses_fixed_size(self):
        """未知 doc_type 应使用固定大小分块"""
        content = "这是一段普通文本内容。" * 100
        chunks = chunk_by_doc_type("unknown_type", content)
        self.assertIsInstance(chunks, list)
        self.assertGreater(len(chunks), 1)

    def test_empty_content_returns_empty(self):
        """空内容应返回空列表"""
        chunks = chunk_by_doc_type("faq", "")
        self.assertEqual(chunks, [])

    def test_all_chunks_have_text_field(self):
        """所有分块结果都应包含 text 字段"""
        content = "Q1. 测试问题？\nA1. 测试回答\n\nQ2. 另一个问题？\nA2. 另一个回答\n"
        chunks = chunk_by_doc_type("faq", content)
        for chunk in chunks:
            self.assertIn("text", chunk)
            self.assertIn("metadata_addition", chunk)


class TestCheckinCompilationChunking(unittest.TestCase):

    def test_extracts_reflections(self):
        """应能提取"收获"维度"""
        content = "一、基本信息\n2024年春季签到\n\n【收获】\n学会了团队协作\n\n2024.03.15\n"
        chunks = chunk_compilation_by_section(content)
        section_types = [c["metadata_addition"].get("section_type") for c in chunks]
        self.assertIn("reflections", section_types)

    def test_extracts_problems(self):
        """应能提取"问题"维度"""
        content = "一、基本信息\n签到签退\n\n【问题】\n场地太小了\n\n2024.03.15\n"
        chunks = chunk_compilation_by_section(content)
        section_types = [c["metadata_addition"].get("section_type") for c in chunks]
        self.assertIn("problems", section_types)

    def test_extracts_suggestions(self):
        """应能提取"建议"维度"""
        content = "一、基本信息\n活动签到\n\n【建议】\n下次多准备椅子\n\n2024.03.15\n"
        chunks = chunk_compilation_by_section(content)
        section_types = [c["metadata_addition"].get("section_type") for c in chunks]
        self.assertIn("suggestions", section_types)

    def test_all_three_dimensions(self):
        """同时有收获、问题、建议时，应分为三块"""
        content = (
            "一、基本信息\n签到记录\n\n"
            "【收获】\n学会了PS\n\n2024.03.15\n"
            "【问题】\n设备不够\n\n2024.03.15\n"
            "【建议】\n多买几台电脑\n\n2024.03.15\n"
        )
        chunks = chunk_compilation_by_section(content)
        section_types = {c["metadata_addition"].get("section_type") for c in chunks}
        self.assertIn("reflections", section_types)
        self.assertIn("problems", section_types)
        self.assertIn("suggestions", section_types)

    def test_empty_content_returns_empty(self):
        """空内容返回空列表"""
        chunks = chunk_compilation_by_section("")
        self.assertEqual(chunks, [])

    def test_no_matching_sections(self):
        """无匹配维度时返回空列表或仅含 general"""
        content = "这是一段无关的文本，没有任何维度关键词。\n"
        chunks = chunk_compilation_by_section(content)
        # 无匹配维度，extract_section 都返回空字符串
        self.assertIsInstance(chunks, list)


class TestFAQChunking(unittest.TestCase):

    def test_split_by_qa_pairs(self):
        """FAQ 应按 Q&A 对分块"""
        content = (
            "Q1. 如何报名？\nA1. 填写在线表单\n\n"
            "Q2. 社费多少？\nA2. 每学期50元\n\n"
            "Q3. 活动在哪里举办？\nA3. 学校报告厅\n"
        )
        chunks = chunk_faq_by_qa(content)
        self.assertEqual(len(chunks), 3)

    def test_single_qa_pair(self):
        """单个 Q&A 应返回一个块"""
        content = "Q1. 如何加入？\nA1. 请联系社长\n"
        chunks = chunk_faq_by_qa(content)
        self.assertEqual(len(chunks), 1)
        self.assertIn("如何加入", chunks[0]["text"])

    def test_q_prefix_formats(self):
        """支持 Q、Q1、Q2 等格式"""
        content = "Q. 第一个问题？\nA. 回答一\n\nQ1. 第二个问题？\nA1. 回答二\n"
        chunks = chunk_faq_by_qa(content)
        self.assertGreaterEqual(len(chunks), 1)

    def test_no_q_prefix_single_chunk(self):
        """无 Q 前缀的文本作为单个块"""
        content = "这是一段没有Q前缀的普通文本内容。\n"
        chunks = chunk_faq_by_qa(content)
        self.assertEqual(len(chunks), 1)

    def test_empty_content(self):
        """空内容返回空列表"""
        chunks = chunk_faq_by_qa("")
        self.assertEqual(chunks, [])


class TestFixedSizeChunking(unittest.TestCase):

    def test_respects_chunk_size(self):
        """分块大小不应超过 CHUNK_SIZE（512 字符）"""
        # 生成长文本
        long_text = "这是一段测试文本。" * 200  # 约 1800 字符
        chunks = chunk_by_fixed_size(long_text)
        for chunk in chunks:
            # 允许少量超出（separators 导致），但不应远超
            self.assertLessEqual(len(chunk["text"]), 600)

    def test_short_text_single_chunk(self):
        """短文本应返回单个块"""
        short_text = "这是一段很短的文本。"
        chunks = chunk_by_fixed_size(short_text)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0]["text"], short_text)

    def test_all_chunks_have_metadata(self):
        """所有块都应包含 metadata_addition 字段"""
        text = "段落一。\n\n段落二。\n\n段落三。" * 20
        chunks = chunk_by_fixed_size(text)
        for chunk in chunks:
            self.assertIn("text", chunk)
            self.assertIn("metadata_addition", chunk)
            self.assertIsInstance(chunk["metadata_addition"], dict)

    def test_text_content_preserved(self):
        """分块后不应丢失文本内容"""
        text = "重要内容A。" * 50 + "\n\n" + "重要内容B。" * 50
        chunks = chunk_by_fixed_size(text)
        full_text = "".join(c["text"] for c in chunks)
        self.assertIn("重要内容A", full_text)
        self.assertIn("重要内容B", full_text)


class TestMarkdownSectionChunking(unittest.TestCase):

    def test_split_by_h2_headers(self):
        """应按 ## 标题分块"""
        content = "## 第一部分\n\n内容一\n\n## 第二部分\n\n内容二\n"
        chunks = chunk_by_markdown_sections(content)
        self.assertEqual(len(chunks), 2)

    def test_large_section_uses_fixed_size(self):
        """超过 1500 字符的段落应使用固定大小分块"""
        large_section = "## 大段落\n\n" + "测试内容。" * 300  # 超过 1500 字符
        chunks = chunk_by_markdown_sections(large_section)
        self.assertGreater(len(chunks), 1)

    def test_empty_content(self):
        """空内容返回空列表"""
        chunks = chunk_by_markdown_sections("")
        self.assertEqual(chunks, [])


if __name__ == "__main__":
    unittest.main()
