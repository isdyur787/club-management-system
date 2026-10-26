package com.club.management.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorCode 单元测试")
class ErrorCodeTest {

    // ======================== 错误码唯一性测试 ========================

    @Test
    @DisplayName("所有错误码 code 值唯一，不存在重复")
    void allCodes_areUnique() {
        ErrorCode[] allCodes = ErrorCode.values();
        Map<Integer, List<ErrorCode>> grouped = Arrays.stream(allCodes)
                .collect(Collectors.groupingBy(ErrorCode::getCode));

        grouped.forEach((code, list) ->
                assertThat(list)
                        .as("错误码 %d 被多个枚举值使用: %s", code, list)
                        .hasSize(1)
        );
    }

    // ======================== 错误码格式正确性测试 ========================

    @Test
    @DisplayName("所有错误码为 5 位正整数（10000-99999）")
    void allCodes_areFiveDigitPositiveIntegers() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            Integer code = errorCode.getCode();
            assertThat(code)
                    .as("ErrorCode.%s 的 code 应为 5 位正整数, 实际为 %s", errorCode.name(), code)
                    .isNotNull()
                    .isPositive()
                    .satisfies(c -> {
                        assertThat(c.toString().length())
                                .as("ErrorCode.%s 的 code '%d' 应为 5 位数字", errorCode.name(), c)
                                .isEqualTo(5);
                    });
        }
    }

    @Test
    @DisplayName("所有错误消息非空且非空白")
    void allMessages_areNotBlank() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getMessage())
                    .as("ErrorCode.%s 的 message 不应为空", errorCode.name())
                    .isNotBlank();
        }
    }

    // ======================== 模块分段正确性测试 ========================

    @Test
    @DisplayName("通用错误码范围: 40001-40099, 42900")
    void generalErrorCodes_inExpectedRange() {
        Set<Integer> generalCodes = Set.of(
                ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.FORBIDDEN.getCode(),
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                ErrorCode.IDEMPOTENT_CONFLICT.getCode(),
                ErrorCode.ACCOUNT_LOCKED.getCode(),
                ErrorCode.RATE_LIMIT_EXCEEDED.getCode()
        );

        for (Integer code : generalCodes) {
            if (code == 42900) continue; // RATE_LIMIT 特殊值
            assertThat(code)
                    .as("通用错误码应在 40001-40099 范围内")
                    .isBetween(40001, 40099);
        }
    }

    @Test
    @DisplayName("用户模块错误码范围: 40101-40199")
    void userModuleCodes_inExpectedRange() {
        ErrorCode[] userCodes = {
                ErrorCode.USER_NOT_FOUND,
                ErrorCode.USER_ALREADY_EXISTS,
                ErrorCode.PASSWORD_INCORRECT,
                ErrorCode.ROLE_NOT_FOUND
        };

        for (ErrorCode ec : userCodes) {
            assertThat(ec.getCode())
                    .as("用户模块 ErrorCode.%s 应在 40101-40199 范围内", ec.name())
                    .isBetween(40101, 40199);
        }
    }

    @Test
    @DisplayName("社团模块错误码范围: 40201-40299")
    void clubModuleCodes_inExpectedRange() {
        ErrorCode[] clubCodes = {
                ErrorCode.CLUB_NOT_FOUND,
                ErrorCode.CLUB_ALREADY_EXISTS,
                ErrorCode.CLUB_NOT_APPROVED,
                ErrorCode.MEMBER_ALREADY_EXISTS,
                ErrorCode.MEMBER_NOT_FOUND,
                ErrorCode.CLUB_PRESIDENT_CANNOT_LEAVE
        };

        for (ErrorCode ec : clubCodes) {
            assertThat(ec.getCode())
                    .as("社团模块 ErrorCode.%s 应在 40201-40299 范围内", ec.name())
                    .isBetween(40201, 40299);
        }
    }

    @Test
    @DisplayName("活动模块错误码范围: 40301-40399")
    void activityModuleCodes_inExpectedRange() {
        ErrorCode[] activityCodes = {
                ErrorCode.ACTIVITY_NOT_FOUND,
                ErrorCode.ACTIVITY_STATUS_INVALID,
                ErrorCode.ACTIVITY_APPROVAL_REJECTED,
                ErrorCode.ACTIVITY_ALREADY_APPROVED,
                ErrorCode.ACTIVITY_BUDGET_EXCEEDED,
                ErrorCode.ACTIVITY_TIME_CONFLICT,
                ErrorCode.ACTIVITY_NOT_COMPLETED,
                ErrorCode.CHECKIN_DATA_EMPTY,
                ErrorCode.REPORT_ALREADY_EXISTS,
                ErrorCode.CHECKIN_TASK_EXISTS,
                ErrorCode.CHECKIN_DEADLINE_PASSED,
                ErrorCode.CHECKIN_ALREADY_SUBMITTED,
                ErrorCode.CHECKIN_TASK_NOT_FOUND,
                ErrorCode.CHECKIN_NOT_SETTLED,
                ErrorCode.CHECKIN_ALREADY_SETTLED,
                ErrorCode.SIGNOUT_NOT_CHECKED_IN,
                ErrorCode.SIGNOUT_ALREADY_DONE,
                ErrorCode.COMPILATION_NOT_DEADLINE,
                ErrorCode.COMPILATION_ALREADY_EXISTS
        };

        for (ErrorCode ec : activityCodes) {
            assertThat(ec.getCode())
                    .as("活动模块 ErrorCode.%s 应在 40301-40399 范围内", ec.name())
                    .isBetween(40301, 40399);
        }
    }

    @Test
    @DisplayName("系统内部错误码范围: 50001-50099")
    void systemErrorCodes_inExpectedRange() {
        ErrorCode[] systemCodes = {
                ErrorCode.SYSTEM_ERROR,
                ErrorCode.DATABASE_ERROR,
                ErrorCode.REDIS_ERROR
        };

        for (ErrorCode ec : systemCodes) {
            assertThat(ec.getCode())
                    .as("系统错误 ErrorCode.%s 应在 50001-50099 范围内", ec.name())
                    .isBetween(50001, 50099);
        }
    }

    // ======================== 枚举完整性测试 ========================

    @Test
    @DisplayName("ErrorCode 枚举值数量合理（不少于 30 个）")
    void errorCode_hasReasonableCount() {
        assertThat(ErrorCode.values().length)
                .as("ErrorCode 枚举应包含至少 30 个错误码")
                .isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("valueOf 可正常解析每个枚举名称")
    void valueOf_allEnumNames_works() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(ErrorCode.valueOf(errorCode.name())).isSameAs(errorCode);
        }
    }
}
