package com.club.management.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.activity.dto.*;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.entity.Approval;
import com.club.management.activity.manager.ApprovalManager;
import com.club.management.activity.manager.ApprovalStrategy;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.activity.mapper.ApprovalMapper;
import com.club.management.activity.service.ActivityService;
import com.club.management.activity.statemachine.ActivityStateMachine;
import com.club.management.activity.vo.ActivityDetailVO;
import com.club.management.activity.vo.ActivityVO;
import com.club.management.club.entity.ClubInfo;
import com.club.management.club.mapper.ClubInfoMapper;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.enums.ActivityStatus;
import com.club.management.common.enums.ActivityType;
import com.club.management.common.enums.ApprovalStatus;
import com.club.management.common.event.ActivityApprovedEvent;
import com.club.management.common.event.ActivityCompletedEvent;
import com.club.management.common.event.ActivityRejectedEvent;
import com.club.management.common.exception.*;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.user.entity.User;
import com.club.management.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final ApprovalMapper approvalMapper;
    private final ClubInfoMapper clubInfoMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final UserMapper userMapper;
    private final ActivityStateMachine stateMachine;
    private final ApprovalManager approvalManager;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityVO createActivity(Long userId, ActivityCreateDTO dto) {
        // 校验时间
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new ValidationException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }

        // 查询用户所属社团
        Long clubId = clubMemberMapper.getClubIdByUserId(userId);
        if (clubId == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "您不属于任何社团");
        }

        // 创建活动
        Activity activity = new Activity();
        activity.setClubId(clubId);
        activity.setTitle(dto.getTitle());
        activity.setDescription(dto.getDescription());
        activity.setType(dto.getType().name());
        activity.setLocation(dto.getLocation());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setRegistrationDeadline(dto.getRegistrationDeadline());
        activity.setMaxParticipants(dto.getMaxParticipants());
        activity.setCurrentParticipants(0);
        activity.setBudget(dto.getBudget());
        activity.setStatus(ActivityStatus.DRAFT.name());
        activity.setVersion(1);
        activity.setCoverImage(dto.getCoverImage());
        activity.setNeedRegistration(dto.getNeedRegistration());
        activity.setNeedApproval(dto.getNeedApproval());

        activityMapper.insert(activity);

        ActivityVO vo = new ActivityVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setStatus(activity.getStatus());
        return vo;
    }

    @Override
    public PageResult<ActivityVO> listActivities(ActivityQueryDTO dto) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        if (dto.getClubId() != null) {
            wrapper.eq(Activity::getClubId, dto.getClubId());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(Activity::getStatus, dto.getStatus());
        }
        if (StringUtils.hasText(dto.getType())) {
            wrapper.eq(Activity::getType, dto.getType());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(Activity::getTitle, dto.getKeyword());
        }
        if (StringUtils.hasText(dto.getStartTimeFrom())) {
            wrapper.ge(Activity::getStartTime, LocalDate.parse(dto.getStartTimeFrom()).atStartOfDay());
        }
        if (StringUtils.hasText(dto.getStartTimeTo())) {
            wrapper.le(Activity::getStartTime, LocalDate.parse(dto.getStartTimeTo()).atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(Activity::getCreatedAt);

        Page<Activity> page = activityMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        // 批量查询社团信息，替代 toActivityVO 中的逐条查询
        Set<Long> clubIds = page.getRecords().stream()
                .map(Activity::getClubId).collect(Collectors.toSet());
        Map<Long, String> clubNameMap = new HashMap<>();
        if (!clubIds.isEmpty()) {
            clubInfoMapper.selectBatchIds(clubIds)
                    .forEach(c -> clubNameMap.put(c.getId(), c.getClubName()));
        }

        List<ActivityVO> voList = page.getRecords().stream()
                .map(a -> toActivityVO(a, clubNameMap))
                .collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    public ActivityDetailVO getActivityDetail(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        ActivityDetailVO vo = new ActivityDetailVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setDescription(activity.getDescription());
        vo.setClubId(activity.getClubId());
        vo.setType(activity.getType());
        vo.setLocation(activity.getLocation());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setRegistrationDeadline(activity.getRegistrationDeadline());
        vo.setMaxParticipants(activity.getMaxParticipants());
        vo.setCurrentParticipants(activity.getCurrentParticipants());
        vo.setBudget(activity.getBudget());
        vo.setStatus(activity.getStatus());
        vo.setSummary(activity.getSummary());
        vo.setCoverImage(activity.getCoverImage());
        vo.setNeedRegistration(Boolean.TRUE.equals(activity.getNeedRegistration()));
        vo.setCreatedAt(activity.getCreatedAt());

        // 查询社团名称
        ClubInfo club = clubInfoMapper.selectById(activity.getClubId());
        if (club != null) {
            vo.setClubName(club.getClubName());
        }

        // 查询创建者信息
        User creator = userMapper.selectById(activity.getCreatedBy());
        if (creator != null) {
            ActivityDetailVO.CreatorVO creatorVO = new ActivityDetailVO.CreatorVO();
            creatorVO.setId(creator.getId());
            creatorVO.setRealName(creator.getRealName());
            vo.setCreator(creatorVO);
        }

        // 查询审批记录
        LambdaQueryWrapper<Approval> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.eq(Approval::getActivityId, id)
                .orderByAsc(Approval::getApprovalLevel);
        List<Approval> approvals = approvalMapper.selectList(approvalWrapper);

        // 批量查询审批人信息，替代逐条查询
        Set<Long> approverIds = approvals.stream()
                .map(Approval::getApproverId)
                .filter(aid -> aid != null)
                .collect(Collectors.toSet());
        Map<Long, User> approverMap = new HashMap<>();
        if (!approverIds.isEmpty()) {
            userMapper.selectBatchIds(approverIds)
                    .forEach(u -> approverMap.put(u.getId(), u));
        }

        List<ActivityDetailVO.ApprovalRecordVO> approvalRecords = approvals.stream().map(a -> {
            ActivityDetailVO.ApprovalRecordVO record = new ActivityDetailVO.ApprovalRecordVO();
            record.setApprovalLevel(a.getApprovalLevel());
            record.setResult(a.getResult());
            record.setComment(a.getComment());
            record.setApprovedAt(a.getApprovedAt());
            User approver = approverMap.get(a.getApproverId());
            if (approver != null) {
                record.setApproverName(approver.getRealName());
            }
            return record;
        }).collect(Collectors.toList());
        vo.setApprovalRecords(approvalRecords);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(Long id, ActivityUpdateDTO dto) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 仅 DRAFT 和 REJECTED 状态可修改
        if (!ActivityStatus.DRAFT.name().equals(activity.getStatus())
                && !ActivityStatus.REJECTED.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID);
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!activity.getCreatedBy().equals(currentUserId)) {
            Long clubId = clubMemberMapper.getClubIdByUserId(currentUserId);
            if (clubId == null || !clubId.equals(activity.getClubId())) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }
        }

        if (dto.getTitle() != null) activity.setTitle(dto.getTitle());
        if (dto.getDescription() != null) activity.setDescription(dto.getDescription());
        if (dto.getLocation() != null) activity.setLocation(dto.getLocation());
        if (dto.getMaxParticipants() != null) activity.setMaxParticipants(dto.getMaxParticipants());
        if (dto.getCoverImage() != null) activity.setCoverImage(dto.getCoverImage());

        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        if (!ActivityStatus.DRAFT.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID);
        }

        activityMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        stateMachine.transition(activity.getStatus(), ActivityStatus.PENDING.name());
        activity.setStatus(ActivityStatus.PENDING.name());
        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveActivity(Long id, Long approverId, ApprovalDTO dto) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 状态检查：PENDING 或 APPROVED（多级审批中间态）
        if (!ActivityStatus.PENDING.name().equals(activity.getStatus())
                && !ActivityStatus.APPROVED.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID);
        }

        // 获取审批策略
        ActivityType type = ActivityType.valueOf(activity.getType());
        ApprovalStrategy strategy = approvalManager.getApprovalStrategy(type);

        // 确定当前审批级别：取下一个未完成的级别
        List<ApprovalStrategy.ApprovalLevel> levels = strategy.getApprovalLevels();
        int currentLevel = 1;
        for (ApprovalStrategy.ApprovalLevel level : levels) {
            boolean alreadyApproved = approvalManager.hasLevelApproved(id, level.getLevel());
            if (!alreadyApproved) {
                currentLevel = level.getLevel();
                break;
            }
        }

        // 检查该审批人是否已在当前级别审批过
        if (approvalManager.hasAlreadyApproved(id, approverId, currentLevel)) {
            throw new BusinessException(ErrorCode.ACTIVITY_ALREADY_APPROVED);
        }

        if (Boolean.FALSE.equals(dto.getApproved())) {
            // 驳回
            approvalManager.saveApprovalRecord(id, approverId, currentLevel, ApprovalStatus.REJECTED.getCode(), dto.getComment());
            approvalManager.rejectActivity(activity, dto.getComment());
            if (activityMapper.updateById(activity) == 0) {
                throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
            }

            eventPublisher.publishEvent(new ActivityRejectedEvent(
                    this, id, activity.getClubId(), dto.getComment()));
        } else {
            // 通过
            approvalManager.saveApprovalRecord(id, approverId, currentLevel, ApprovalStatus.APPROVED.getCode(), dto.getComment());

            // 检查是否所有级别都已通过
            if (approvalManager.checkAllApproved(activity)) {
                approvalManager.approveActivity(activity);
                if (activityMapper.updateById(activity) == 0) {
                    throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
                }

                eventPublisher.publishEvent(new ActivityApprovedEvent(
                        this, id, activity.getClubId(), approverId));
            }
            // 否则保持 PENDING 状态
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        stateMachine.transition(activity.getStatus(), ActivityStatus.IN_PROGRESS.name());
        activity.setStatus(ActivityStatus.IN_PROGRESS.name());
        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeActivity(Long id, CompleteDTO dto) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        stateMachine.transition(activity.getStatus(), ActivityStatus.COMPLETED.name());
        activity.setStatus(ActivityStatus.COMPLETED.name());
        activity.setSummary(dto.getSummary());
        if (activityMapper.updateById(activity) == 0) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "数据已被其他人修改，请刷新后重试");
        }

        eventPublisher.publishEvent(new ActivityCompletedEvent(
                this, id, activity.getClubId(), activity.getCurrentParticipants()));
    }

    private ActivityVO toActivityVO(Activity activity) {
        return toActivityVO(activity, null);
    }

    private ActivityVO toActivityVO(Activity activity, Map<Long, String> clubNameMap) {
        ActivityVO vo = new ActivityVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setClubId(activity.getClubId());
        vo.setType(activity.getType());
        vo.setLocation(activity.getLocation());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setMaxParticipants(activity.getMaxParticipants());
        vo.setCurrentParticipants(activity.getCurrentParticipants());
        vo.setBudget(activity.getBudget());
        vo.setStatus(activity.getStatus());
        vo.setCoverImage(activity.getCoverImage());
        vo.setNeedRegistration(Boolean.TRUE.equals(activity.getNeedRegistration()));
        vo.setCreatedAt(activity.getCreatedAt());

        if (clubNameMap != null) {
            vo.setClubName(clubNameMap.get(activity.getClubId()));
        } else {
            ClubInfo club = clubInfoMapper.selectById(activity.getClubId());
            if (club != null) {
                vo.setClubName(club.getClubName());
            }
        }
        return vo;
    }
}
