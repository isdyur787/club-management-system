"""
安全护栏（Guardrails）— 输入/输出双向内容安全过滤

设计模式：Chain of Responsibility — 多个检查器依次执行，任一拦截则终止
"""
import re
import time
import logging
from collections import defaultdict
from typing import Tuple, Optional

logger = logging.getLogger(__name__)


class Guardrails:
    """输入/输出双向安全护栏"""

    # Prompt 注入检测模式
    INJECTION_PATTERNS = [
        r"忽略以上指令",
        r"忽略之前的",
        r"你现在是",
        r"告诉我系统",
        r"system\s*prompt",
        r"不要遵守",
        r"假装你是",
        r"进入开发者模式",
        r"输出你的.*指令",
        r"DAN\s*模式",
        r"developer\s*mode",
    ]

    # 敏感信息脱敏规则
    SENSITIVE_RULES = [
        (r"\b1[3-9]\d{9}\b", "[手机号已脱敏]"),
        (r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b", "[邮箱已脱敏]"),
        (r"(?:password|密码|token|secret|api_key)\s*[:=]\s*\S+", "[敏感信息已脱敏]"),
    ]

    TOPIC_KEYWORDS = [
        "社团", "活动", "签到", "报名", "积分", "打卡", "报告", "审核", "汇编", "成员",
        "知识库", "章程", "招新", "社员", "入社", "社费", "社规", "协会", "英语角"
    ]

    def __init__(self, rate_limit_per_minute: int = 20):
        self._rate_limit_per_minute = rate_limit_per_minute
        self._rate_limit_records = defaultdict(list)

    def check_input(self, query: str) -> Tuple[bool, str]:
        """
        输入安全检查
        返回: (is_safe, reason)
        """
        if not query or not query.strip():
            return False, "请输入您的问题"

        if len(query) > 500:
            return False, "问题过长（超过500字），请简化您的提问"

        query_lower = query.lower()
        for pattern in self.INJECTION_PATTERNS:
            if re.search(pattern, query_lower, re.IGNORECASE):
                logger.warning(f"Prompt injection detected: pattern={pattern}")
                return False, "检测到潜在的安全风险，请重新提问"

        return True, ""

    def check_topic_allowed(self, query: str) -> Tuple[bool, str]:
        """话题白名单：仅允许社团业务相关提问，避免模型被用作通用助手"""
        query_text = query.strip().lower()
        if not query_text:
            return False, "请输入您的问题"

        if any(keyword in query_text for keyword in self.TOPIC_KEYWORDS):
            return True, ""

        # 社团名称常以“社”结尾（如街舞社、电竞社），不一定包含“社团”。
        if re.search(r"[\u4e00-\u9fffA-Za-z0-9]{2,20}社", query_text):
            return True, ""

        return False, "当前助手仅支持社团管理相关问题（活动/签到/报名/积分/报告等）"

    def check_rate_limit(self, key: Optional[str]) -> Tuple[bool, str]:
        """接口级速率限制，防刷接口"""
        if not key:
            return True, ""

        now = time.time()
        window_start = now - 60
        records = self._rate_limit_records[key]
        self._rate_limit_records[key] = [t for t in records if t > window_start]

        if len(self._rate_limit_records[key]) >= self._rate_limit_per_minute:
            return False, "请求过于频繁，请稍后再试"

        self._rate_limit_records[key].append(now)
        return True, ""

    def filter_output(self, answer: str) -> str:
        """输出敏感信息脱敏"""
        # 部分 OpenAI 兼容模型会把推理过程写入 content，并以 </think> 结束。
        # 对外只保留最终答案，避免在 UI 中暴露内部推理草稿。
        filtered = re.sub(r"(?is)^.*?</think>\s*", "", answer).strip()
        for pattern, replacement in self.SENSITIVE_RULES:
            filtered = re.sub(pattern, replacement, filtered, flags=re.IGNORECASE)
        return filtered
