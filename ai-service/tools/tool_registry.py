"""
工具注册表（Tool Registry）— 管理所有可调用工具及其 Schema

设计模式：Registry 模式 — 集中管理工具定义，新增工具无需修改调用方
"""
import json
import logging
import threading
from typing import Dict, Callable, List

logger = logging.getLogger(__name__)


class ToolRegistry:
    """工具注册表"""

    _tools: Dict[str, dict] = None
    _functions: Dict[str, Callable] = None
    _lock = threading.Lock()

    def __init__(self):
        """初始化实例级别的工具注册表（避免类变量可变对象共享问题）"""
        if ToolRegistry._tools is None:
            ToolRegistry._tools = {}
        if ToolRegistry._functions is None:
            ToolRegistry._functions = {}

    @classmethod
    def _ensure_init(cls):
        """确保类级别数据结构已初始化（线程安全）"""
        if cls._tools is None:
            with cls._lock:
                if cls._tools is None:
                    cls._tools = {}
                    cls._functions = {}

    @classmethod
    def register(cls, name: str, description: str, parameters: dict, func: Callable):
        cls._ensure_init()
        cls._tools[name] = {
            "type": "function",
            "function": {
                "name": name,
                "description": description,
                "parameters": parameters
            }
        }
        cls._functions[name] = func
        logger.info(f"Tool registered: {name}")

    @classmethod
    def get_openai_tools(cls) -> List[dict]:
        """获取 OpenAI Function Calling 格式的工具列表"""
        cls._ensure_init()
        return list(cls._tools.values())

    @classmethod
    def execute(cls, name: str, arguments: dict) -> dict:
        """执行工具调用（带参数白名单验证）"""
        cls._ensure_init()
        if name not in cls._functions:
            return {"error": f"未知工具: {name}"}
        try:
            # 参数白名单验证：只传递 schema 中声明的参数
            schema = cls._tools.get(name, {}).get("function", {}).get("parameters", {})
            allowed_params = set(schema.get("properties", {}).keys())
            if allowed_params:
                filtered_args = {k: v for k, v in arguments.items() if k in allowed_params}
                dropped = set(arguments.keys()) - allowed_params
                if dropped:
                    logger.warning(f"Tool '{name}': dropped unexpected arguments: {dropped}")
            else:
                filtered_args = arguments

            result = cls._functions[name](**filtered_args)
            logger.info(f"Tool executed: {name}({filtered_args})")
            return result
        except Exception as e:
            logger.error(f"Tool execution failed: {name}: {e}")
            return {"error": f"工具执行失败: {name}"}

    @classmethod
    def has_tool(cls, name: str) -> bool:
        cls._ensure_init()
        return name in cls._functions


def _init_tools():
    """注册所有工具 — 工厂模式：每次调用时创建新的 DBReader，避免连接泄漏"""
    from tools.db_reader import DBReader

    def _make_caller(method_name):
        def _caller(**kwargs):
            with DBReader() as reader:
                return getattr(reader, method_name)(**kwargs)
        return _caller

    ToolRegistry.register(
        name="search_activities",
        description="搜索社团活动列表，可按状态、关键词筛选",
        parameters={
            "type": "object",
            "properties": {
                "club_id": {"type": "integer", "description": "社团ID"},
                "status": {
                    "type": "string",
                    "enum": ["DRAFT", "APPROVED", "IN_PROGRESS", "COMPLETED", "ARCHIVED"],
                    "description": "活动状态筛选"
                },
                "keyword": {"type": "string", "description": "搜索关键词"}
            },
            "required": ["club_id"]
        },
        func=_make_caller("search_activities")
    )

    ToolRegistry.register(
        name="get_club_info",
        description="获取社团详细信息，包括成员数、简介、社长等",
        parameters={
            "type": "object",
            "properties": {
                "club_id": {"type": "integer", "description": "社团ID"}
            },
            "required": ["club_id"]
        },
        func=_make_caller("get_club_detail")
    )

    ToolRegistry.register(
        name="get_activity_statistics",
        description="获取活动的参与统计（报名人数、签到率、平均评分等）",
        parameters={
            "type": "object",
            "properties": {
                "activity_id": {"type": "integer", "description": "活动ID"}
            },
            "required": ["activity_id"]
        },
        func=_make_caller("get_activity_stats")
    )

    ToolRegistry.register(
        name="get_member_list",
        description="获取社团成员列表，包括角色和加入时间",
        parameters={
            "type": "object",
            "properties": {
                "club_id": {"type": "integer", "description": "社团ID"},
                "role": {
                    "type": "string",
                    "enum": ["PRESIDENT", "VICE_PRESIDENT", "MEMBER"],
                    "description": "成员角色筛选"
                }
            },
            "required": ["club_id"]
        },
        func=_make_caller("get_club_members")
    )

    ToolRegistry.register(
        name="list_all_clubs",
        description="获取所有社团列表概览，包括社团名称、类别、成员数、社长等信息。当用户询问“有哪些社团”、“所有社团”、“社团列表”时调用",
        parameters={
            "type": "object",
            "properties": {},
            "required": []
        },
        func=_make_caller("list_all_clubs")
    )


# 延迟初始化（在首次使用时注册工具，线程安全）
_initialized = False
_init_lock = threading.Lock()

def ensure_tools_initialized():
    global _initialized
    if not _initialized:
        with _init_lock:
            if not _initialized:  # 双重检查锁定
                try:
                    _init_tools()
                    _initialized = True
                except Exception as e:
                    logger.error(f"Tool initialization failed: {e}")
