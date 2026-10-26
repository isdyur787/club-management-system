import sys
import os
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class TestLangGraphWorkflowCompilation(unittest.TestCase):
    def test_report_graph_compiles_when_langgraph_available(self):
        try:
            from routes.report import _HAS_LANGGRAPH_REPORT, _build_report_graph
            if not _HAS_LANGGRAPH_REPORT:
                self.skipTest("langgraph not available")
            graph = _build_report_graph()
            self.assertIsNotNone(graph)
        except Exception as e:
            self.fail(f"report graph compile failed: {e}")

    def test_insight_graph_compiles_when_langgraph_available(self):
        try:
            from routes.insights_agent import _HAS_LANGGRAPH_INSIGHT, _build_insight_graph
            if not _HAS_LANGGRAPH_INSIGHT:
                self.skipTest("langgraph not available")
            graph = _build_insight_graph()
            self.assertIsNotNone(graph)
        except Exception as e:
            self.fail(f"insight graph compile failed: {e}")


if __name__ == "__main__":
    unittest.main()
