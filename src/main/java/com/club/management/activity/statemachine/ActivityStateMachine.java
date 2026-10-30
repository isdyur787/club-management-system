package com.club.management.activity.statemachine;

import com.club.management.common.enums.ActivityStatus;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 活动状态转换的规则表，判断当前状态能否到达目标状态；本类只校验，实际状态赋值和数据库更新由调用方完成。
 */
@Component
public class ActivityStateMachine {

    // 左侧是当前状态，右侧是允许到达的下一状态；未列出的跳转一律拒绝。
    private static final Map<ActivityStatus, Set<ActivityStatus>> TRANSITIONS = Map.of(
            ActivityStatus.DRAFT, Set.of(ActivityStatus.PENDING, ActivityStatus.ARCHIVED),
            ActivityStatus.PENDING, Set.of(ActivityStatus.APPROVED, ActivityStatus.REJECTED),
            ActivityStatus.APPROVED, Set.of(ActivityStatus.IN_PROGRESS, ActivityStatus.REJECTED),
            ActivityStatus.REJECTED, Set.of(ActivityStatus.DRAFT),
            ActivityStatus.IN_PROGRESS, Set.of(ActivityStatus.COMPLETED),
            ActivityStatus.COMPLETED, Set.of(ActivityStatus.ARCHIVED),
            ActivityStatus.ARCHIVED, Set.of()
    );

    // 这里只验证规则，不写数据库；调用方校验成功后仍需赋值并保存活动。
    public void transition(String currentStatus, String targetStatus) {
        ActivityStatus from;
        ActivityStatus to;
        try {
            from = ActivityStatus.valueOf(currentStatus);
            to = ActivityStatus.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID,
                    "无效的活动状态: " + currentStatus + " 或 " + targetStatus);
        }
        Set<ActivityStatus> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID,
                    from + " 不能转换到 " + to);
        }
    }

    public boolean canTransition(String currentStatus, String targetStatus) {
        try {
            ActivityStatus from = ActivityStatus.valueOf(currentStatus);
            ActivityStatus to = ActivityStatus.valueOf(targetStatus);
            Set<ActivityStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
            return allowed.contains(to);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Set<ActivityStatus> getAvailableTransitions(String currentStatus) {
        ActivityStatus from = ActivityStatus.valueOf(currentStatus);
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}
