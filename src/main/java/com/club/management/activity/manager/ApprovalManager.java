package com.club.management.activity.manager;

import com.club.management.activity.entity.Activity;
import com.club.management.activity.entity.Approval;
import com.club.management.activity.mapper.ApprovalMapper;
import com.club.management.activity.statemachine.ActivityStateMachine;
import com.club.management.common.enums.ActivityType;
import com.club.management.common.enums.ActivityStatus;
import com.club.management.common.enums.ApprovalStatus;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApprovalManager {

    @Autowired
    private ApprovalStrategyFactory strategyFactory;

    @Autowired
    private ApprovalMapper approvalMapper;

    @Autowired
    private ActivityStateMachine stateMachine;

    public ApprovalStrategy getApprovalStrategy(ActivityType type) {
        return strategyFactory.getStrategy(type);
    }

    public boolean checkAllApproved(Activity activity) {
        ActivityType type = ActivityType.valueOf(activity.getType());
        ApprovalStrategy strategy = strategyFactory.getStrategy(type);
        List<ApprovalStrategy.ApprovalLevel> requiredLevels = strategy.getApprovalLevels();

        // 单次聚合查询替代 N 次 count 查询（企业级 N+1 优化）
        List<Integer> requiredLevelNumbers = requiredLevels.stream()
                .map(ApprovalStrategy.ApprovalLevel::getLevel)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Approval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Approval::getActivityId, activity.getId())
                .in(Approval::getApprovalLevel, requiredLevelNumbers)
                .eq(Approval::getResult, ApprovalStatus.APPROVED.getCode())
                .select(Approval::getApprovalLevel)
                .groupBy(Approval::getApprovalLevel);
        List<Approval> approvedLevels = approvalMapper.selectList(wrapper);

        Set<Integer> approvedLevelNumbers = approvedLevels.stream()
                .map(Approval::getApprovalLevel)
                .collect(Collectors.toSet());

        return approvedLevelNumbers.containsAll(requiredLevelNumbers);
    }

    public boolean hasAlreadyApproved(Long activityId, Long approverId, Integer level) {
        LambdaQueryWrapper<Approval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Approval::getActivityId, activityId)
                .eq(Approval::getApproverId, approverId)
                .eq(Approval::getApprovalLevel, level);
        return approvalMapper.selectCount(wrapper) > 0;
    }

    public boolean hasLevelApproved(Long activityId, Integer level) {
        LambdaQueryWrapper<Approval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Approval::getActivityId, activityId)
                .eq(Approval::getApprovalLevel, level)
                .eq(Approval::getResult, ApprovalStatus.APPROVED.getCode());
        return approvalMapper.selectCount(wrapper) > 0;
    }

    public void saveApprovalRecord(Long activityId, Long approverId, Integer level,
                                    Integer result, String comment) {
        Approval approval = new Approval();
        approval.setActivityId(activityId);
        approval.setApproverId(approverId);
        approval.setApprovalLevel(level);
        approval.setResult(result);
        approval.setComment(comment);
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.insert(approval);
    }

    public void rejectActivity(Activity activity, String reason) {
        stateMachine.transition(activity.getStatus(), ActivityStatus.REJECTED.name());
        activity.setStatus(ActivityStatus.REJECTED.name());
    }

    public void approveActivity(Activity activity) {
        stateMachine.transition(activity.getStatus(), ActivityStatus.APPROVED.name());
        activity.setStatus(ActivityStatus.APPROVED.name());
    }
}
