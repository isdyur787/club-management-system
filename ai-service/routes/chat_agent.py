"""
chat_agent.py — ReAct Agent 循环

包含原生 Function Calling 的多轮 ReAct (Reasoning + Acting) 循环。
"""

import json
import logging
from typing import Optional, Dict, List, Any

from routes.chat_deps import _validate_fc_tool_arguments, _self_check_fc_answer

logger = logging.getLogger(__name__)


# ========== 原生 Function Calling（通过 ModelRouter 调度） ==========

def _try_function_calling(question: str, club_id: int, history: List[Dict] = None) -> Optional[Dict]:
    """
    ReAct Agent 循环：多轮 Function Calling

    设计模式：ReAct (Reasoning + Acting)
    - LLM 思考 → 调用工具 → 观察结果 → 再思考 → ... → 最终回答
    - 最大迭代次数防止无限循环
    - 主模型失败时 ModelRouter 自动降级
    - 工具参数强制校验，防止模型越权改写关键参数
    """
    MAX_ITERATIONS = 5  # 防止无限循环

    try:
        from tools.tool_registry import ToolRegistry, ensure_tools_initialized
        from agents.model_router import get_model_router

        ensure_tools_initialized()
        tools = ToolRegistry.get_openai_tools()
        if not tools:
            return None

        router = get_model_router()

        # 初始消息
        messages = [
            {"role": "system", "content": (
                "你是一个社团管理助手。当用户询问实时数据（活动列表、成员数量、统计数字等）时，"
                "请调用相应工具获取数据。你可以多次调用工具来获取完整信息。"
                "如果是知识问答，直接回答即可。"
                f"当前用户的社团ID为: {club_id}"
            )},
        ]
        messages.extend((history or [])[-10:])
        messages.append({"role": "user", "content": question})

        all_tool_names = []  # 记录所有调用过的工具

        for iteration in range(MAX_ITERATIONS):
            # Step 1: 发送带 tools 的请求
            result = router.call_with_fallback(
                task_type="function_calling",
                messages=messages,
                temperature=0,
                tools=tools
            )

            response = result["response"]
            message = response.choices[0].message

            # LLM 没有调用工具 → 返回最终回答
            if not message.tool_calls:
                if iteration == 0:
                    # 第一轮就没调工具 → 走 RAG 路径
                    logger.info(f"FC: LLM chose RAG for: {question[:50]}")
                    return None

                # 多轮后 LLM 决定不再调工具 → 这就是最终回答
                answer = message.content
                sources = [{"title": name, "content": "", "doc_type": "tool_result"}
                           for name in set(all_tool_names)]

                self_check = _self_check_fc_answer(answer, list(set(all_tool_names)))
                check_passed = bool(self_check.get("passed", True))
                check_confidence = float(self_check.get("confidence", 0.5))
                if not check_passed:
                    answer = f"⚠️ 以下回答可能不完全准确，仅供参考：\n\n{answer}"

                logger.info(
                    f"ReAct: completed in {iteration} iteration(s), "
                    f"tools used: {', '.join(set(all_tool_names))}"
                )
                return {
                    "answer": answer,
                    "sources": sources,
                    "check_passed": check_passed,
                    "check_confidence": check_confidence
                }

            # Step 2: 执行工具调用
            messages.append(message)  # assistant 的 tool_calls 消息
            logger.info(f"ReAct iter={iteration}: LLM called {len(message.tool_calls)} tool(s)")

            for tool_call in message.tool_calls:
                func_name = tool_call.function.name
                all_tool_names.append(func_name)

                try:
                    func_args = json.loads(tool_call.function.arguments)
                except json.JSONDecodeError:
                    func_args = {}

                func_args = _validate_fc_tool_arguments(func_name, func_args, club_id)

                logger.info(f"ReAct: executing {func_name}({func_args})")
                tool_result = ToolRegistry.execute(func_name, func_args)

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": json.dumps(tool_result, ensure_ascii=False)
                })

        # 超过最大迭代，让 LLM 基于已有信息生成回答（不带 tools）
        logger.warning(f"ReAct: max iterations ({MAX_ITERATIONS}) reached, generating answer")
        final_result = router.call_with_fallback(
            task_type="rag",
            messages=messages,
            temperature=0.7
        )
        answer = final_result["response"].choices[0].message.content
        sources = [{"title": name, "content": "", "doc_type": "tool_result"}
                   for name in set(all_tool_names)]

        self_check = _self_check_fc_answer(answer, list(set(all_tool_names)))
        check_passed = bool(self_check.get("passed", True))
        check_confidence = float(self_check.get("confidence", 0.5))
        if not check_passed:
            answer = f"⚠️ 以下回答可能不完全准确，仅供参考：\n\n{answer}"

        return {"answer": answer, "sources": sources, "check_passed": check_passed, "check_confidence": check_confidence}

    except Exception as e:
        logger.warning(f"Function Calling failed: {e}, falling back to RAG")
        return None
