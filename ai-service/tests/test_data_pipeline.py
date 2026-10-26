import sys
import os
import time
import unittest
from collections import OrderedDict
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.guardrails import Guardrails
from rag.latency import RetrievalCache


class TestGuardrailsDataFlow(unittest.TestCase):
    def test_topic_guard_business_keywords(self):
        g = Guardrails()
        ok, reason = g.check_topic_allowed("帮我看看本周活动报名情况")
        self.assertTrue(ok)
        self.assertEqual(reason, "")

    def test_topic_guard_blocks_non_business(self):
        g = Guardrails()
        ok, reason = g.check_topic_allowed("帮我写一首诗")
        self.assertFalse(ok)
        self.assertIn("社团管理", reason)

    def test_rate_limit_isolation_between_keys(self):
        g = Guardrails(rate_limit_per_minute=1)
        self.assertTrue(g.check_rate_limit("u1")[0])
        self.assertFalse(g.check_rate_limit("u1")[0])
        self.assertTrue(g.check_rate_limit("u2")[0])

    def test_rate_limit_window_reset(self):
        g = Guardrails(rate_limit_per_minute=1)
        g.check_rate_limit("u3")
        g._rate_limit_records["u3"] = [time.time() - 61]
        ok, _ = g.check_rate_limit("u3")
        self.assertTrue(ok)


class TestRetrievalCacheBehavior(unittest.TestCase):
    def setUp(self):
        self.cache = RetrievalCache.__new__(RetrievalCache)
        self.cache._cache = OrderedDict()
        self.cache._ttl = 1
        self.cache._max_size = 2
        self.cache._hits = 0
        self.cache._misses = 0
        self.cache._initialized = True

    def test_put_get_hit(self):
        self.cache.put("c", "q", 3, [{"id": 1}])
        self.assertEqual(self.cache.get("c", "q", 3), [{"id": 1}])

    def test_ttl_expiry(self):
        self.cache.put("c", "q", 3, [{"id": 1}])
        key = next(iter(self.cache._cache))
        self.cache._cache[key]["timestamp"] = time.time() - 2
        self.assertIsNone(self.cache.get("c", "q", 3))

    def test_lru_eviction(self):
        self.cache.put("c", "q1", 3, [{"id": 1}])
        self.cache.put("c", "q2", 3, [{"id": 2}])
        self.cache.get("c", "q1", 3)
        self.cache.put("c", "q3", 3, [{"id": 3}])
        self.assertIsNone(self.cache.get("c", "q2", 3))
        self.assertIsNotNone(self.cache.get("c", "q1", 3))

    def test_stats_counting(self):
        self.cache.put("c", "q", 3, [{"id": 1}])
        self.cache.get("c", "q", 3)
        self.cache.get("c", "missing", 3)
        stats = self.cache.get_stats()
        self.assertEqual(stats["hits"], 1)
        self.assertEqual(stats["misses"], 1)


class TestChatGuardrailsComposition(unittest.TestCase):
    @patch("redis.from_url", return_value=MagicMock())
    def test_validate_request_guardrails_blocks_topic(self, _redis_mock):
        import routes.chat as chat_module
        ok, reason = chat_module._validate_request_guardrails("帮我写代码", club_id=1, rate_limit_key="k")
        self.assertFalse(ok)
        self.assertIn("社团管理", reason)


class TestFCValidation(unittest.TestCase):
    @patch("redis.from_url", return_value=MagicMock())
    def test_validate_fc_tool_arguments_enforces_club_id(self, _redis_mock):
        import routes.chat as chat_module
        from tools.tool_registry import ToolRegistry, ensure_tools_initialized
        ensure_tools_initialized()

        ToolRegistry._tools["search_activities"] = {
            "type": "function",
            "function": {
                "name": "search_activities",
                "parameters": {
                    "type": "object",
                    "properties": {"club_id": {"type": "integer"}},
                    "required": ["club_id"]
                }
            }
        }

        args = chat_module._validate_fc_tool_arguments("search_activities", {"keyword": "活动"}, club_id=7)
        self.assertEqual(args["club_id"], 7)

    @patch("redis.from_url", return_value=MagicMock())
    def test_validate_fc_tool_arguments_blocks_override(self, _redis_mock):
        import routes.chat as chat_module
        from tools.tool_registry import ToolRegistry, ensure_tools_initialized
        ensure_tools_initialized()

        ToolRegistry._tools["search_activities"] = {
            "type": "function",
            "function": {
                "name": "search_activities",
                "parameters": {
                    "type": "object",
                    "properties": {"club_id": {"type": "integer"}},
                    "required": ["club_id"]
                }
            }
        }

        args = chat_module._validate_fc_tool_arguments("search_activities", {"club_id": 999, "keyword": "活动"}, club_id=7)
        self.assertEqual(args["club_id"], 7)


if __name__ == "__main__":
    unittest.main()
