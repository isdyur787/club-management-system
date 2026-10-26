"""
工具执行引擎 — 负责调用 ToolRegistry 执行工具并格式化结果

设计模式：Executor 模式 — 统一工具执行入口，集中错误处理
"""
import json
import logging
from typing import Dict, List
from tools.tool_registry import ToolRegistry, ensure_tools_initialized

logger = logging.getLogger(__name__)


def execute_tool_calls(tool_calls: list) -> List[Dict]:
    """
    批量执行工具调用

    参数：
        tool_calls: OpenAI tool_calls 列表

    返回：
        [{"tool": name, "tool_call_id": id, "result": dict}, ...]
    """
    ensure_tools_initialized()
    results = []

    for tc in tool_calls:
        name = tc.function.name
        try:
            arguments = json.loads(tc.function.arguments)
        except json.JSONDecodeError:
            arguments = {}

        result = ToolRegistry.execute(name, arguments)
        results.append({
            "tool": name,
            "tool_call_id": tc.id,
            "result": result
        })
        logger.info(f"Tool call: {name}({arguments}) → {str(result)[:200]}")

    return results


def format_tool_results_for_llm(results: List[Dict]) -> str:
    """将工具执行结果格式化为 LLM 可读的文本"""
    parts = []
    for r in results:
        parts.append(f"[{r['tool']}] 结果：{json.dumps(r['result'], ensure_ascii=False)}")
    return "\n".join(parts)
