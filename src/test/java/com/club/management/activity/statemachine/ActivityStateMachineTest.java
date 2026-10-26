package com.club.management.activity.statemachine;

import com.club.management.common.enums.ActivityStatus;
import com.club.management.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityStateMachine 单元测试")
class ActivityStateMachineTest {

    @InjectMocks
    private ActivityStateMachine stateMachine;

    // ======================== 合法状态转换测试 ========================

    @Nested
    @DisplayName("合法状态转换 (transition)")
    class ValidTransitionTests {

        @ParameterizedTest(name = "{0} → {1} 应该成功")
        @DisplayName("合法转换不应抛出异常")
        @CsvSource({
                "DRAFT, PENDING",
                "DRAFT, ARCHIVED",
                "PENDING, APPROVED",
                "PENDING, REJECTED",
                "APPROVED, IN_PROGRESS",
                "APPROVED, REJECTED",
                "REJECTED, DRAFT",
                "IN_PROGRESS, COMPLETED",
                "COMPLETED, ARCHIVED"
        })
        void validTransition_shouldNotThrow(String from, String to) {
            assertThatCode(() -> stateMachine.transition(from, to))
                    .doesNotThrowAnyException();
        }
    }

    // ======================== 非法状态转换测试 ========================

    @Nested
    @DisplayName("非法状态转换 (transition)")
    class InvalidTransitionTests {

        @ParameterizedTest(name = "{0} → {1} 应该抛出异常")
        @DisplayName("非法转换应抛出 BusinessException")
        @CsvSource({
                "DRAFT, APPROVED",
                "DRAFT, IN_PROGRESS",
                "DRAFT, COMPLETED",
                "DRAFT, REJECTED",
                "PENDING, DRAFT",
                "PENDING, IN_PROGRESS",
                "PENDING, COMPLETED",
                "PENDING, ARCHIVED",
                "APPROVED, DRAFT",
                "APPROVED, PENDING",
                "APPROVED, COMPLETED",
                "APPROVED, ARCHIVED",
                "REJECTED, PENDING",
                "REJECTED, APPROVED",
                "REJECTED, IN_PROGRESS",
                "REJECTED, COMPLETED",
                "REJECTED, ARCHIVED",
                "IN_PROGRESS, DRAFT",
                "IN_PROGRESS, PENDING",
                "IN_PROGRESS, APPROVED",
                "IN_PROGRESS, REJECTED",
                "IN_PROGRESS, ARCHIVED",
                "COMPLETED, DRAFT",
                "COMPLETED, PENDING",
                "COMPLETED, APPROVED",
                "COMPLETED, REJECTED",
                "COMPLETED, IN_PROGRESS",
                "ARCHIVED, DRAFT",
                "ARCHIVED, PENDING",
                "ARCHIVED, APPROVED",
                "ARCHIVED, REJECTED",
                "ARCHIVED, IN_PROGRESS",
                "ARCHIVED, COMPLETED"
        })
        void invalidTransition_shouldThrow(String from, String to) {
            assertThatThrownBy(() -> stateMachine.transition(from, to))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能转换到");
        }
    }

    // ======================== canTransition 测试 ========================

    @Nested
    @DisplayName("canTransition 方法")
    class CanTransitionTests {

        @Test
        @DisplayName("合法转换返回 true")
        void canTransition_valid_returnsTrue() {
            assertThat(stateMachine.canTransition("DRAFT", "PENDING")).isTrue();
            assertThat(stateMachine.canTransition("PENDING", "APPROVED")).isTrue();
            assertThat(stateMachine.canTransition("IN_PROGRESS", "COMPLETED")).isTrue();
        }

        @Test
        @DisplayName("非法转换返回 false")
        void canTransition_invalid_returnsFalse() {
            assertThat(stateMachine.canTransition("DRAFT", "APPROVED")).isFalse();
            assertThat(stateMachine.canTransition("ARCHIVED", "DRAFT")).isFalse();
            assertThat(stateMachine.canTransition("COMPLETED", "IN_PROGRESS")).isFalse();
        }

        @Test
        @DisplayName("无效状态名返回 false")
        void canTransition_invalidStatus_returnsFalse() {
            assertThat(stateMachine.canTransition("INVALID", "DRAFT")).isFalse();
            assertThat(stateMachine.canTransition("DRAFT", "INVALID")).isFalse();
        }
    }

    // ======================== getAvailableTransitions 测试 ========================

    @Nested
    @DisplayName("getAvailableTransitions 方法")
    class GetAvailableTransitionsTests {

        @Test
        @DisplayName("DRAFT 可转换到 PENDING 和 ARCHIVED")
        void draft_transitions() {
            Set<ActivityStatus> transitions = stateMachine.getAvailableTransitions("DRAFT");
            assertThat(transitions).containsExactlyInAnyOrder(
                    ActivityStatus.PENDING, ActivityStatus.ARCHIVED);
        }

        @Test
        @DisplayName("ARCHIVED 没有可用转换")
        void archived_noTransitions() {
            Set<ActivityStatus> transitions = stateMachine.getAvailableTransitions("ARCHIVED");
            assertThat(transitions).isEmpty();
        }

        @Test
        @DisplayName("APPROVED 可转换到 IN_PROGRESS 和 REJECTED")
        void approved_transitions() {
            Set<ActivityStatus> transitions = stateMachine.getAvailableTransitions("APPROVED");
            assertThat(transitions).containsExactlyInAnyOrder(
                    ActivityStatus.IN_PROGRESS, ActivityStatus.REJECTED);
        }
    }
}
