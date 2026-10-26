"""
ModelRouter 模块单元测试

测试要点：
- 主模型调用成功
- 主模型失败降级到备用模型
- 统计计数器正确性
"""
import sys
import os
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from agents.model_router import ModelRouter, ModelProfile


def _make_mock_response(content="OK"):
    """构造模拟的 OpenAI ChatCompletion 响应"""
    response = MagicMock()
    message = MagicMock()
    message.content = content
    choice = MagicMock()
    choice.message = message
    response.choices = [choice]
    return response


def _make_profile(name, model="test-model", supports_fc=True):
    """构造测试用 ModelProfile"""
    profile = ModelProfile(
        name=name,
        api_key="test-key",
        base_url="http://localhost",
        model=model,
        timeout=10,
        temperature=0.7,
        supports_function_calling=supports_fc,
    )
    # 替换 OpenAI client
    mock_client = MagicMock()
    profile._client = mock_client
    return profile


class TestModelRouterBasic(unittest.TestCase):

    def test_register_and_get_profile(self):
        """注册后可按名称获取"""
        router = ModelRouter()
        profile = _make_profile("primary")
        router.register(profile, task_types=["rag", "default"])

        result = router.get_profile("rag")
        self.assertEqual(result.name, "primary")

    def test_get_profile_fallback_to_primary(self):
        """未绑定的任务类型应降级到 primary"""
        router = ModelRouter()
        primary = _make_profile("primary")
        router.register(primary, task_types=["rag"])

        result = router.get_profile("unknown_task")
        self.assertEqual(result.name, "primary")

    def test_get_profile_raises_when_empty(self):
        """无注册模型时应抛出异常"""
        router = ModelRouter()
        with self.assertRaises(RuntimeError):
            router.get_profile("rag")


class TestModelRouterCallSuccess(unittest.TestCase):

    def test_primary_model_call_success(self):
        """主模型调用成功时返回正确响应"""
        router = ModelRouter()
        primary = _make_profile("primary", model="deepseek-v4")
        primary._client.chat.completions.create.return_value = _make_mock_response("主模型回答")
        router.register(primary, task_types=["rag"])

        result = router.call_with_fallback("rag", [{"role": "user", "content": "你好"}])
        self.assertEqual(result["response"].choices[0].message.content, "主模型回答")
        self.assertEqual(result["profile"].name, "primary")

    def test_response_contains_profile(self):
        """返回结果应包含使用的 profile 信息"""
        router = ModelRouter()
        profile = _make_profile("primary", model="gpt-4")
        profile._client.chat.completions.create.return_value = _make_mock_response("回答")
        router.register(profile)

        result = router.call_with_fallback("default", [{"role": "user", "content": "问题"}])
        self.assertIn("response", result)
        self.assertIn("profile", result)
        self.assertEqual(result["profile"].model, "gpt-4")

    def test_temperature_passed_to_api(self):
        """temperature 参数应正确传递到 API"""
        router = ModelRouter()
        profile = _make_profile("primary")
        profile._client.chat.completions.create.return_value = _make_mock_response()
        router.register(profile)

        router.call_with_fallback("default", [{"role": "user", "content": "问题"}], temperature=0.3)
        call_kwargs = profile._client.chat.completions.create.call_args[1]
        self.assertEqual(call_kwargs["temperature"], 0.3)

    def test_max_tokens_passed_when_specified(self):
        """max_tokens 参数应正确传递"""
        router = ModelRouter()
        profile = _make_profile("primary")
        profile._client.chat.completions.create.return_value = _make_mock_response()
        router.register(profile)

        router.call_with_fallback("default", [{"role": "user", "content": "问题"}], max_tokens=100)
        call_kwargs = profile._client.chat.completions.create.call_args[1]
        self.assertEqual(call_kwargs["max_tokens"], 100)

    def test_default_temperature_from_profile(self):
        """未指定 temperature 时应使用 profile 默认值"""
        router = ModelRouter()
        profile = _make_profile("primary")
        profile.temperature = 0.5
        profile._client.chat.completions.create.return_value = _make_mock_response()
        router.register(profile)

        router.call_with_fallback("default", [{"role": "user", "content": "问题"}])
        call_kwargs = profile._client.chat.completions.create.call_args[1]
        self.assertEqual(call_kwargs["temperature"], 0.5)


class TestModelRouterFallback(unittest.TestCase):

    def test_primary_fail_fallback_to_secondary(self):
        """主模型失败时应自动降级到备用模型"""
        router = ModelRouter()
        primary = _make_profile("primary", model="deepseek")
        primary._client.chat.completions.create.side_effect = TimeoutError("请求超时")
        fallback = _make_profile("fallback", model="mimo")
        fallback._client.chat.completions.create.return_value = _make_mock_response("备用回答")

        router.register(primary, task_types=["rag"])
        router.register(fallback)

        result = router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        self.assertEqual(result["response"].choices[0].message.content, "备用回答")
        self.assertEqual(result["profile"].name, "fallback")

    def test_all_models_fail_raises(self):
        """所有模型都失败时应抛出 RuntimeError"""
        router = ModelRouter()
        primary = _make_profile("primary", model="model_a")
        primary._client.chat.completions.create.side_effect = RuntimeError("错误1")
        fallback = _make_profile("fallback", model="model_b")
        fallback._client.chat.completions.create.side_effect = RuntimeError("错误2")

        router.register(primary, task_types=["rag"])
        router.register(fallback)

        with self.assertRaises(RuntimeError) as ctx:
            router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        self.assertIn("All models failed", str(ctx.exception))

    def test_fallback_chain_order(self):
        """降级链应先尝试 preferred 模型，再尝试其他模型"""
        router = ModelRouter()
        p1 = _make_profile("primary", model="m1")
        p1._client.chat.completions.create.side_effect = RuntimeError("失败")
        p2 = _make_profile("secondary", model="m2")
        p2._client.chat.completions.create.side_effect = RuntimeError("失败")
        p3 = _make_profile("tertiary", model="m3")
        p3._client.chat.completions.create.return_value = _make_mock_response("最终成功")

        router.register(p1, task_types=["rag"])
        router.register(p2)
        router.register(p3)

        result = router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        self.assertEqual(result["profile"].name, "tertiary")

    def test_tools_filter_non_fc_models(self):
        """传入 tools 时应过滤掉不支持 FC 的模型，但如果全部不支持则保留链"""
        router = ModelRouter()
        p_fc = _make_profile("fc_model", supports_fc=True)
        p_fc._client.chat.completions.create.side_effect = RuntimeError("失败")
        p_no_fc = _make_profile("no_fc_model", supports_fc=False)
        p_no_fc._client.chat.completions.create.return_value = _make_mock_response("降级")

        router.register(p_fc, task_types=["function_calling"])
        router.register(p_no_fc)

        tools = [{"type": "function", "function": {"name": "test_func"}}]
        # fc_model 失败后，no_fc_model 因为不支持 fc 会被过滤
        # 但过滤后为空，所以 fallback 回全链
        result = router.call_with_fallback("function_calling", [{"role": "user", "content": "调用工具"}], tools=tools)
        self.assertIsNotNone(result)

    def test_preferred_model_checked_first(self):
        """首选模型应被第一个尝试调用"""
        router = ModelRouter()
        call_order = []

        p1 = _make_profile("primary", model="m1")
        def side_effect_1(*args, **kwargs):
            call_order.append("primary")
            return _make_mock_response("OK")
        p1._client.chat.completions.create.side_effect = side_effect_1

        p2 = _make_profile("fallback", model="m2")
        def side_effect_2(*args, **kwargs):
            call_order.append("fallback")
            return _make_mock_response("OK")
        p2._client.chat.completions.create.side_effect = side_effect_2

        router.register(p1, task_types=["rag"])
        router.register(p2)

        router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        self.assertEqual(call_order[0], "primary")


class TestModelRouterStats(unittest.TestCase):

    def test_stats_initialized_on_register(self):
        """注册时应初始化调用统计"""
        router = ModelRouter()
        profile = _make_profile("primary")
        router.register(profile)

        stats = router.get_stats()
        self.assertIn("primary", stats)
        self.assertEqual(stats["primary"]["calls"], 0)
        self.assertEqual(stats["primary"]["failures"], 0)

    def test_stats_count_success(self):
        """成功调用应增加 calls 计数"""
        router = ModelRouter()
        profile = _make_profile("primary")
        profile._client.chat.completions.create.return_value = _make_mock_response()
        router.register(profile)

        router.call_with_fallback("default", [{"role": "user", "content": "问题"}])
        stats = router.get_stats()
        self.assertEqual(stats["primary"]["calls"], 1)
        self.assertEqual(stats["primary"]["failures"], 0)

    def test_stats_count_failure(self):
        """失败调用应增加 failures 计数"""
        router = ModelRouter()
        primary = _make_profile("primary")
        primary._client.chat.completions.create.side_effect = RuntimeError("错误")
        fallback = _make_profile("fallback")
        fallback._client.chat.completions.create.return_value = _make_mock_response()
        router.register(primary, task_types=["rag"])
        router.register(fallback)

        router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        stats = router.get_stats()
        self.assertEqual(stats["primary"]["calls"], 1)
        self.assertEqual(stats["primary"]["failures"], 1)
        self.assertEqual(stats["fallback"]["calls"], 1)
        self.assertEqual(stats["fallback"]["failures"], 0)

    def test_stats_accumulate(self):
        """多次调用统计应累积"""
        router = ModelRouter()
        profile = _make_profile("primary")
        profile._client.chat.completions.create.return_value = _make_mock_response()
        router.register(profile)

        for _ in range(5):
            router.call_with_fallback("default", [{"role": "user", "content": "问题"}])

        stats = router.get_stats()
        self.assertEqual(stats["primary"]["calls"], 5)

    def test_get_stats_returns_copy(self):
        """get_stats 返回副本，修改不影响内部状态"""
        router = ModelRouter()
        profile = _make_profile("primary")
        router.register(profile)

        stats = router.get_stats()
        stats["primary"]["calls"] = 999

        self.assertEqual(router.get_stats()["primary"]["calls"], 0)

    def test_stats_per_model_independent(self):
        """不同模型的统计相互独立"""
        router = ModelRouter()
        p1 = _make_profile("primary")
        p1._client.chat.completions.create.return_value = _make_mock_response()
        p2 = _make_profile("fallback")
        p2._client.chat.completions.create.return_value = _make_mock_response()
        router.register(p1, task_types=["rag"])
        router.register(p2)

        router.call_with_fallback("rag", [{"role": "user", "content": "问题"}])
        stats = router.get_stats()
        self.assertEqual(stats["primary"]["calls"], 1)
        self.assertEqual(stats["fallback"]["calls"], 0)


class TestModelProfileClient(unittest.TestCase):

    def test_get_client_returns_cached_instance(self):
        """多次调用 get_client 应返回同一实例"""
        # ModelProfile 用真实 OpenAI client，但 _client 字段可手动设置
        profile = _make_profile("test")
        client1 = profile.get_client()
        client2 = profile.get_client()
        self.assertIs(client1, client2)


if __name__ == "__main__":
    unittest.main()
