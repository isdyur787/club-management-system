"""
工具注册表单元测试

测试要点：
- Registry 模式：注册/查询/执行工具
- 错误处理：未知工具、执行异常
- OpenAI 格式输出
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import unittest
from tools.tool_registry import ToolRegistry


class TestToolRegistry(unittest.TestCase):

    def setUp(self):
        """每次测试前重置注册表"""
        ToolRegistry._tools = {}
        ToolRegistry._functions = {}

    def test_register_and_query(self):
        """注册工具后可通过 has_tool 和 get_openai_tools 查询"""
        ToolRegistry.register(
            name="test_tool",
            description="测试工具",
            parameters={"type": "object", "properties": {}},
            func=lambda: {"result": "ok"}
        )
        self.assertTrue(ToolRegistry.has_tool("test_tool"))
        self.assertFalse(ToolRegistry.has_tool("nonexistent"))
        
        tools = ToolRegistry.get_openai_tools()
        self.assertEqual(len(tools), 1)
        self.assertEqual(tools[0]["function"]["name"], "test_tool")

    def test_execute_tool(self):
        """执行已注册工具"""
        ToolRegistry.register(
            name="add",
            description="加法",
            parameters={"type": "object"},
            func=lambda a, b: {"sum": a + b}
        )
        result = ToolRegistry.execute("add", {"a": 3, "b": 5})
        self.assertEqual(result["sum"], 8)

    def test_execute_unknown_tool(self):
        """执行未知工具返回错误"""
        result = ToolRegistry.execute("unknown", {})
        self.assertIn("error", result)

    def test_execute_with_exception(self):
        """工具执行异常时返回错误信息"""
        def bad_func():
            raise ValueError("模拟错误")
        
        ToolRegistry.register(
            name="bad_tool",
            description="会报错的工具",
            parameters={"type": "object"},
            func=bad_func
        )
        result = ToolRegistry.execute("bad_tool", {})
        self.assertIn("error", result)

    def test_multiple_tools(self):
        """注册多个工具互不干扰"""
        ToolRegistry.register("tool_a", "A", {}, lambda: {"a": 1})
        ToolRegistry.register("tool_b", "B", {}, lambda: {"b": 2})
        
        self.assertEqual(len(ToolRegistry.get_openai_tools()), 2)
        self.assertEqual(ToolRegistry.execute("tool_a", {})["a"], 1)
        self.assertEqual(ToolRegistry.execute("tool_b", {})["b"], 2)


class TestStructuredOutputSchemas(unittest.TestCase):
    """测试 Pydantic Schema 定义"""

    def test_self_check_result_schema(self):
        from rag.structured_output import SelfCheckResult
        r = SelfCheckResult(passed=True, confidence=0.9, reason="测试")
        self.assertTrue(r.passed)
        self.assertEqual(r.confidence, 0.9)

    def test_experience_suggestion_schema(self):
        from rag.structured_output import ExperienceSuggestion
        r = ExperienceSuggestion(
            experience_summary="总结",
            suggestions=["建议1", "建议2"],
            risk_warnings=["风险1"]
        )
        self.assertEqual(len(r.suggestions), 2)

    def test_activity_plan_schema(self):
        from rag.structured_output import ActivityPlan
        r = ActivityPlan(
            timeline=["T-7天：发布通知"],
            budget_reference=500.0,
            venue_suggestion="教室A101",
            risk_warnings=["天气"]
        )
        self.assertEqual(r.budget_reference, 500.0)

    def test_default_values(self):
        """Schema 默认值应正确"""
        from rag.structured_output import SelfCheckResult, NotificationSummary, RegistrationReview
        self.assertTrue(SelfCheckResult().passed)
        self.assertEqual(SelfCheckResult().confidence, 0.5)
        self.assertEqual(NotificationSummary().summary, "")
        self.assertEqual(RegistrationReview().ranked_users, [])


if __name__ == "__main__":
    unittest.main()
