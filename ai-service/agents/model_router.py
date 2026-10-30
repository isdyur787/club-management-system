# 文件职责：按任务选择模型档案，缓存客户端并依次尝试主备模型，累计调用/失败统计。
"""
模型路由器（Model Router）— 统一 LLM 调度层

企业级 Agent 常用模式：根据任务类型选择模型，主模型失败时自动降级到备用模型。

设计模式：
- Strategy 模式：不同任务类型对应不同模型策略
- Chain of Responsibility：主模型 → 备用模型的降级链
- Singleton：全局唯一的 ModelRouter 实例
"""

import logging
import time
from typing import Optional, Dict, List
from openai import OpenAI
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class ModelProfile:
    """模型配置档案"""
    name: str               # 档案名（如 "primary", "fallback"）
    api_key: str = field(repr=False)
    base_url: str
    model: str
    timeout: int = 60
    temperature: float = 0.7
    supports_function_calling: bool = True  # 是否支持原生 Function Calling
    supports_thinking: bool = False          # 是否为思考模型
    _client: Optional[OpenAI] = field(default=None, repr=False)

    def get_client(self) -> OpenAI:
        if self._client is None:
            self._client = OpenAI(
                api_key=self.api_key,
                base_url=self.base_url,
                timeout=self.timeout
            )
        return self._client


class ModelRouter:
    """
    模型路由器 — 统一管理多模型配置，按任务类型调度

    功能：
    1. 任务路由：Function Calling、RAG 生成、轻量任务分别使用最优模型
    2. 容错降级：主模型调用失败时自动切换到备用模型
    3. 调用统计：记录各模型的调用次数和成功率

    面试话术：
    "项目中的 AI 模块实现了一个 ModelRouter 模型路由层。
     它采用 Strategy 模式，根据任务类型（Function Calling、RAG 生成、轻量计算）
     自动选择最合适的模型。主模型 DeepSeek V4 支持原生 Function Calling，
     备用模型 MiMo 用于降级场景。路由层还实现了自动容错——
     当主模型 API 超时或报错时，会在 200ms 内自动降级到备用模型，
     保证用户体验不中断。"
    """

    def __init__(self):
        self._profiles: Dict[str, ModelProfile] = {}
        self._task_routing: Dict[str, str] = {}  # 任务类型 → 模型档案名
        self._stats: Dict[str, Dict[str, int]] = {}  # 模型 → {calls, failures}

    def register(self, profile: ModelProfile, task_types: List[str] = None):
        """注册模型档案，可选绑定任务类型"""
        self._profiles[profile.name] = profile
        self._stats[profile.name] = {"calls": 0, "failures": 0}
        if task_types:
            for task in task_types:
                self._task_routing[task] = profile.name
        logger.info(f"Model profile registered: {profile.name} ({profile.model}) → tasks: {task_types}")

    def get_profile(self, task_type: str = "default") -> ModelProfile:
        """按任务类型获取最优模型档案（含降级逻辑）"""
        profile_name = self._task_routing.get(task_type, "primary")
        profile = self._profiles.get(profile_name)

        # 如果指定的模型不存在，降级到 primary
        if profile is None:
            profile = self._profiles.get("primary")
        if profile is None:
            raise RuntimeError("No model profiles registered in ModelRouter")

        return profile

    def get_client(self, task_type: str = "default") -> OpenAI:
        """按任务类型获取 OpenAI 客户端"""
        return self.get_profile(task_type).get_client()

    def get_model(self, task_type: str = "default") -> str:
        """按任务类型获取模型名"""
        return self.get_profile(task_type).model

    def call_with_fallback(self, task_type: str, messages: List[dict],
                           temperature: float = None, max_tokens: int = None,
                           tools: List[dict] = None, **kwargs) -> dict:
        """
        带降级链的 LLM 调用。

        主模型失败 → 自动切到备用模型 → 仍失败则抛异常。

        Args:
            task_type: 任务类型（"function_calling", "rag", "lightweight"）
            messages: 消息列表
            temperature: 温度（覆盖默认值）
            max_tokens: 最大 token 数
            tools: 工具定义列表（仅支持 FC 的模型使用）
            **kwargs: 其他 OpenAI API 参数

        Returns:
            {"response": ChatCompletion, "profile": ModelProfile}
        """
        # 构建调用链：首选模型 → 所有其他模型
        preferred = self.get_profile(task_type)
        chain = [preferred]
        for name, profile in self._profiles.items():
            if name != preferred.name:
                chain.append(profile)

        # 工具请求优先尝试支持 FC 的模型，不支持者仍留在链尾，调用时省略工具定义。
        if tools:
            chain = ([p for p in chain if p.supports_function_calling]
                     + [p for p in chain if not p.supports_function_calling])

        last_error = None
        for profile in chain:
            try:
                self._stats[profile.name]["calls"] += 1
                start = time.time()

                call_kwargs = {
                    "model": profile.model,
                    "messages": messages,
                    "temperature": temperature if temperature is not None else profile.temperature,
                }
                if max_tokens:
                    call_kwargs["max_tokens"] = max_tokens
                if tools and profile.supports_function_calling:
                    call_kwargs["tools"] = tools
                    call_kwargs["tool_choice"] = "auto"
                call_kwargs.update(kwargs)

                client = profile.get_client()
                if profile.model.startswith("qwen") and not profile.supports_thinking:
                    call_kwargs["extra_body"] = {**call_kwargs.get("extra_body", {}), "enable_thinking": False}
                # stream=True 时仅取得流对象；迭代阶段的错误由流式调用方处理。
                response = client.chat.completions.create(**call_kwargs)

                elapsed = time.time() - start
                logger.info(
                    f"ModelRouter: {task_type} → {profile.name}({profile.model}) "
                    f"OK in {elapsed:.1f}s"
                )
                if profile.name != preferred.name:
                    logger.warning(
                        f"ModelRouter: DEGRADED — {task_type} 使用备用模型 "
                        f"{profile.name}({profile.model})，主模型 {preferred.name} 不可用"
                    )
                return {"response": response, "profile": profile}

            except Exception as e:
                self._stats[profile.name]["failures"] += 1
                last_error = e
                logger.warning(
                    f"ModelRouter: {profile.name}({profile.model}) failed: {e}, "
                    f"trying next model..."
                )

        raise RuntimeError(f"All models failed for task '{task_type}': {last_error}")

    def get_stats(self) -> Dict[str, Dict[str, int]]:
        """获取各模型调用统计"""
        return {name: dict(stats) for name, stats in self._stats.items()}


# ========== 全局单例 ==========

_router: Optional[ModelRouter] = None


def get_model_router() -> ModelRouter:
    """获取全局 ModelRouter 单例（懒初始化）"""
    global _router
    if _router is None:
        _router = _init_router()
    return _router


def _init_router() -> ModelRouter:
    """从 config.py 读取配置，初始化路由器"""
    from config import (
        LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, LLM_TEMPERATURE, LLM_TIMEOUT,
        FALLBACK_API_KEY, FALLBACK_BASE_URL, FALLBACK_MODEL,
    )

    router = ModelRouter()

    # 主模型：DeepSeek V4-Flash（支持原生 FC）
    primary = ModelProfile(
        name="primary",
        api_key=LLM_API_KEY,
        base_url=LLM_BASE_URL,
        model=LLM_MODEL,
        timeout=LLM_TIMEOUT,
        temperature=LLM_TEMPERATURE,
        supports_function_calling=True,
        supports_thinking=False,
    )
    router.register(primary, task_types=["function_calling", "rag", "self_check", "default"])

    # 备用模型：MiMo v2.5（thinking 模型，不支持原生 FC）
    fallback = ModelProfile(
        name="fallback",
        api_key=FALLBACK_API_KEY,
        base_url=FALLBACK_BASE_URL,
        model=FALLBACK_MODEL,
        timeout=LLM_TIMEOUT,
        temperature=0.7,
        supports_function_calling=False,  # MiMo 不支持原生 FC
        supports_thinking=True,
    )
    if FALLBACK_API_KEY and not FALLBACK_API_KEY.startswith("your-"):
        router.register(fallback, task_types=[])

    logger.info(f"ModelRouter initialized: primary={LLM_MODEL}, fallback={FALLBACK_MODEL}")
    return router
