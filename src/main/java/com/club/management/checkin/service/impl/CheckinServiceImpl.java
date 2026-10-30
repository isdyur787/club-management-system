package com.club.management.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.checkin.dto.CheckinRecordSubmitDTO;
import com.club.management.checkin.dto.CheckinTaskCreateDTO;
import com.club.management.checkin.entity.CheckinRecord;
import com.club.management.checkin.entity.CheckinTask;
import com.club.management.checkin.entity.Compilation;
import com.club.management.checkin.mapper.CheckinRecordMapper;
import com.club.management.checkin.mapper.CheckinTaskMapper;
import com.club.management.checkin.mapper.CompilationMapper;
import com.club.management.checkin.service.CheckinService;
import com.club.management.checkin.vo.CheckinRecordVO;
import com.club.management.checkin.vo.CheckinTaskVO;
import com.club.management.checkin.vo.CompilationVO;
import com.club.management.club.entity.ClubMember;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.config.PointsProperties;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.enums.CheckinTaskStatus;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.response.PageResult;

import com.club.management.common.util.SecurityUtil;
import com.club.management.points.entity.PointsLog;
import com.club.management.points.mapper.PointsLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 反馈打卡服务实现，校验任务期限和提交内容，保存成员反馈，生成积分流水及成员反馈汇编。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final CheckinTaskMapper taskMapper;
    private final CheckinRecordMapper recordMapper;
    private final CompilationMapper compilationMapper;
    private final ActivityMapper activityMapper;
    private final ClubMemberMapper clubMemberMapper;
    private final PointsLogMapper pointsLogMapper;
    private final ObjectMapper objectMapper;
    private final PointsProperties pointsProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinTaskVO createTask(CheckinTaskCreateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 校验活动存在
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 校验打卡任务不存在
        LambdaQueryWrapper<CheckinTask> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(CheckinTask::getActivityId, dto.getActivityId());
        if (taskMapper.selectCount(existingWrapper) > 0) {
            throw new BusinessException(ErrorCode.CHECKIN_TASK_EXISTS);
        }

        // 创建打卡任务
        CheckinTask task = new CheckinTask();
        task.setActivityId(dto.getActivityId());
        task.setClubId(activity.getClubId());
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setRequireReflection(dto.getRequireReflection() ? 1 : 0);
        task.setRequirePhoto(dto.getRequirePhoto() ? 1 : 0);
        task.setRequireSuggestion(dto.getRequireSuggestion() ? 1 : 0);
        task.setDeadline(LocalDateTime.now().plusHours(dto.getDeadlineHours()));
        task.setStatus(CheckinTaskStatus.ACTIVE.getCode());
        taskMapper.insert(task);

        // 此处统计社团成员数，作为任务要求人数；并非活动报名表中的报名人数。
        LambdaQueryWrapper<ClubMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(ClubMember::getClubId, activity.getClubId());
        int requireCount = Math.toIntExact(clubMemberMapper.selectCount(memberWrapper));

        CheckinTaskVO vo = new CheckinTaskVO();
        vo.setId(task.getId());
        vo.setActivityId(task.getActivityId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setRequireReflection(task.getRequireReflection() == 1);
        vo.setRequirePhoto(task.getRequirePhoto() == 1);
        vo.setRequireSuggestion(task.getRequireSuggestion() == 1);
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setSubmittedCount(0);
        vo.setRequireCount(requireCount);
        return vo;
    }

    @Override
    public CheckinTaskVO getTaskByActivityId(Long activityId) {
        LambdaQueryWrapper<CheckinTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(wrapper);
        if (task == null) {
            return null;
        }

        // 单条聚合 SQL 替代 selectCount + selectList 两次查询
        Map<String, Object> stats = recordMapper.selectTaskStats(task.getId());
        int submittedCount = ((Number) stats.get("submitted_count")).intValue();
        double avgRating = ((Number) stats.get("avg_rating")).doubleValue();

        // 查询社团成员数
        int requireCount = Math.toIntExact(clubMemberMapper.selectCount(
                new LambdaQueryWrapper<ClubMember>().eq(ClubMember::getClubId, task.getClubId())));

        CheckinTaskVO vo = new CheckinTaskVO();
        vo.setId(task.getId());
        vo.setActivityId(task.getActivityId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setRequireReflection(task.getRequireReflection() == 1);
        vo.setRequirePhoto(task.getRequirePhoto() == 1);
        vo.setRequireSuggestion(task.getRequireSuggestion() == 1);
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setSubmittedCount(submittedCount);
        vo.setRequireCount(requireCount);
        vo.setAvgRating(avgRating);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinRecordVO submitRecord(CheckinRecordSubmitDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 校验打卡任务存在
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, dto.getActivityId());
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.CHECKIN_TASK_NOT_FOUND);
        }

        // 校验是否已截止
        if (LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new BusinessException(ErrorCode.CHECKIN_DEADLINE_PASSED);
        }

        // 校验是否已提交
        LambdaQueryWrapper<CheckinRecord> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(CheckinRecord::getTaskId, task.getId())
                .eq(CheckinRecord::getUserId, userId);
        if (recordMapper.selectCount(existingWrapper) > 0) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_SUBMITTED);
        }

        // 校验必填字段
        if (task.getRequireReflection() == 1 && (dto.getReflection() == null || dto.getReflection().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写心得感悟");
        }
        if (task.getRequireSuggestion() == 1 && (dto.getSuggestion() == null || dto.getSuggestion().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写改进建议");
        }

        // 创建打卡记录
        CheckinRecord record = new CheckinRecord();
        record.setTaskId(task.getId());
        record.setActivityId(dto.getActivityId());
        record.setClubId(task.getClubId());
        record.setUserId(userId);
        record.setRating(dto.getRating());
        record.setReflection(dto.getReflection());
        record.setProblem(dto.getProblem());
        record.setSuggestion(dto.getSuggestion());
        try {
            record.setPhotoUrls(dto.getPhotoUrls() != null ? objectMapper.writeValueAsString(dto.getPhotoUrls()) : "[]");
            record.setFileUrls(dto.getFileUrls() != null ? objectMapper.writeValueAsString(dto.getFileUrls()) : "[]");
        } catch (JsonProcessingException e) {
            record.setPhotoUrls("[]");
            record.setFileUrls("[]");
        }
        record.setImageDescriptions("[]");
        record.setIsAdopted(CheckinRecord.NOT_ADOPTED);
        record.setStatus(ActiveStatus.ACTIVE.getCode());
        recordMapper.insert(record);

        // 返回VO
        CheckinRecordVO vo = new CheckinRecordVO();
        vo.setId(record.getId());
        vo.setActivityId(record.getActivityId());
        vo.setUserId(record.getUserId());
        vo.setRating(record.getRating());
        vo.setReflection(record.getReflection());
        vo.setProblem(record.getProblem());
        vo.setSuggestion(record.getSuggestion());
        vo.setIsAdopted(false);
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    @Override
    public PageResult<CheckinRecordVO> getRecordsByActivityId(Long activityId, int pageNum, int pageSize) {
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            return new PageResult<>(pageNum, pageSize, 0L, new ArrayList<>());
        }

        LambdaQueryWrapper<CheckinRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckinRecord::getTaskId, task.getId())
                .orderByDesc(CheckinRecord::getCreatedAt);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CheckinRecord> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        recordMapper.selectPage(page, wrapper);

        List<CheckinRecordVO> voList = page.getRecords().stream().map(this::toRecordVO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, page.getTotal(), voList);
    }

    @Override
    public CheckinRecordVO getMyRecord(Long activityId) {
        Long userId = SecurityUtil.getCurrentUserId();
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            return null;
        }

        LambdaQueryWrapper<CheckinRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckinRecord::getTaskId, task.getId())
                .eq(CheckinRecord::getUserId, userId);
        CheckinRecord record = recordMapper.selectOne(wrapper);
        return record != null ? toRecordVO(record) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinRecordVO adoptSuggestion(Long id) {
        CheckinRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        record.setIsAdopted(CheckinRecord.ADOPTED);
        recordMapper.updateById(record);

        // 建议采纳奖励从配置读取，默认 5 分；流水和采纳标记一起提交或回滚。
        PointsLog pointsLog = new PointsLog();
        pointsLog.setClubId(record.getClubId());
        pointsLog.setUserId(record.getUserId());
        pointsLog.setPoints(pointsProperties.getAdoptedSuggestion());
        pointsLog.setReason("ADOPTED_SUGGESTION");
        pointsLog.setDescription("建议被团长采纳");
        pointsLog.setRelatedActivityId(record.getActivityId());
        pointsLog.setRelatedCheckinId(record.getId());
        pointsLogMapper.insert(pointsLog);

        return toRecordVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settlePoints(Long activityId) {
        // 校验打卡任务存在且已截止
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.CHECKIN_TASK_NOT_FOUND);
        }
        if (LocalDateTime.now().isBefore(task.getDeadline())) {
            throw new BusinessException(ErrorCode.CHECKIN_NOT_SETTLED);
        }
        if (task.getStatus() == CheckinTaskStatus.COMPLETED.getCode()) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_SETTLED);
        }
        if (task.getStatus() == CheckinTaskStatus.CLOSED.getCode()) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_SETTLED, "打卡积分正在结算中");
        }

        // CLOSED 表示结算处理中，COMPLETED 表示已结算；后续请求据此识别重复操作。
        // 这里是普通按 ID 更新，状态检查本身不等同于并发锁。
        task.setStatus(CheckinTaskStatus.CLOSED.getCode());
        taskMapper.updateById(task);

        // 获取所有打卡记录
        LambdaQueryWrapper<CheckinRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(CheckinRecord::getTaskId, task.getId());
        List<CheckinRecord> records = recordMapper.selectList(recordWrapper);

        int settledCount = 0;
        int totalPoints = 0;

        // 先在内存中构建所有积分日志，再批量插入
        List<PointsLog> allLogs = new ArrayList<>();

        for (CheckinRecord record : records) {
            // 签到
            allLogs.add(buildPointsLog(record.getClubId(), record.getUserId(), pointsProperties.getSignIn(), "SIGN_IN",
                    "签到成功", activityId, record.getId()));

            // 心得
            if (record.getReflection() != null && !record.getReflection().isBlank()) {
                allLogs.add(buildPointsLog(record.getClubId(), record.getUserId(), pointsProperties.getCheckinReflection(), "CHECKIN_REFLECTION",
                        "提交活动打卡心得", activityId, record.getId()));
            }

            // 照片
            if (record.getPhotoUrls() != null && !record.getPhotoUrls().equals("[]")) {
                allLogs.add(buildPointsLog(record.getClubId(), record.getUserId(), pointsProperties.getCheckinPhoto(), "CHECKIN_PHOTO",
                        "打卡含照片", activityId, record.getId()));
            }

            // 建议
            if (record.getSuggestion() != null && !record.getSuggestion().isBlank()) {
                allLogs.add(buildPointsLog(record.getClubId(), record.getUserId(), pointsProperties.getCheckinSuggestion(), "CHECKIN_SUGGESTION",
                        "提交打卡建议", activityId, record.getId()));
            }

            settledCount++;
        }

        // 积分流水和任务完成状态共用本方法的事务；批量写入失败时整个结算回滚。
        // 批量插入积分日志
        if (!allLogs.isEmpty()) {
            pointsLogMapper.batchInsert(allLogs);
        }
        totalPoints = allLogs.stream().mapToInt(PointsLog::getPoints).sum();

        // 更新任务状态为已结算
        task.setStatus(CheckinTaskStatus.COMPLETED.getCode());
        taskMapper.updateById(task);

        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("settledCount", settledCount);
        result.put("totalPoints", totalPoints);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompilationVO generateCompilation(Long activityId) {
        CheckinTask task = validateCompilationTask(activityId);
        String content = buildCompilationContent(task, activityId);
        return saveCompilation(task, activityId, content);
    }

    /**
     * 校验打卡任务存在且已过截止时间，且汇编尚未生成
     */
    private CheckinTask validateCompilationTask(Long activityId) {
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.CHECKIN_TASK_NOT_FOUND);
        }
        if (LocalDateTime.now().isBefore(task.getDeadline())) {
            throw new BusinessException(ErrorCode.COMPILATION_NOT_DEADLINE);
        }

        LambdaQueryWrapper<Compilation> compWrapper = new LambdaQueryWrapper<>();
        compWrapper.eq(Compilation::getTaskId, task.getId());
        if (compilationMapper.selectCount(compWrapper) > 0) {
            throw new BusinessException(ErrorCode.COMPILATION_ALREADY_EXISTS);
        }
        return task;
    }

    /**
     * 查询数据并拼接汇编文档内容
     */
    private String buildCompilationContent(CheckinTask task, Long activityId) {
        LambdaQueryWrapper<CheckinRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(CheckinRecord::getTaskId, task.getId());
        List<CheckinRecord> records = recordMapper.selectList(recordWrapper);

        Activity activity = activityMapper.selectById(activityId);

        StringBuilder content = new StringBuilder();
        content.append("《").append(activity.getTitle()).append("》成员反馈汇编\n\n");

        // 一、基本信息
        content.append("一、基本信息\n");
        content.append("- 活动时间：").append(activity.getStartTime()).append(" ~ ").append(activity.getEndTime()).append("\n");
        content.append("- 活动地点：").append(activity.getLocation()).append("\n");
        content.append("- 打卡提交数：").append(records.size()).append("\n");

        double avgRating = records.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(CheckinRecord::getRating)
                .average()
                .orElse(0.0);
        content.append("- 平均评分：").append(String.format("%.1f", avgRating)).append("/5\n\n");

        // 二、成员反馈明细
        content.append("二、成员反馈明细\n\n");
        for (int i = 0; i < records.size(); i++) {
            CheckinRecord record = records.get(i);
            content.append(i + 1).append(". 成员").append(record.getUserId())
                    .append("（评分：").append(record.getRating()).append("/5）\n");

            if (record.getReflection() != null && !record.getReflection().isBlank()) {
                content.append("   【收获】").append(record.getReflection()).append("\n");
            }
            if (record.getProblem() != null && !record.getProblem().isBlank()) {
                content.append("   【问题】").append(record.getProblem()).append("\n");
            }
            if (record.getSuggestion() != null && !record.getSuggestion().isBlank()) {
                content.append("   【建议】").append(record.getSuggestion()).append("\n");
            }

            try {
                List<String> photoUrls = objectMapper.readValue(record.getPhotoUrls() != null ? record.getPhotoUrls() : "[]",
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                if (!photoUrls.isEmpty()) {
                    content.append("   【附件】照片 ").append(photoUrls.size()).append(" 张\n");
                }
            } catch (JsonProcessingException e) {
                // ignore
            }
            content.append("\n");
        }

        // 三、问题汇总
        List<String> problems = records.stream()
                .map(CheckinRecord::getProblem)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toList());
        content.append("三、问题汇总\n");
        if (!problems.isEmpty()) {
            content.append("共 ").append(problems.size()).append(" 位成员提出问题\n");
            problems.forEach(p -> content.append("- ").append(p).append("\n"));
        } else {
            content.append("本次无成员提出问题。\n");
        }
        content.append("\n");

        // 四、建议汇总
        List<String> suggestions = records.stream()
                .map(CheckinRecord::getSuggestion)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
        content.append("四、建议汇总\n");
        if (!suggestions.isEmpty()) {
            content.append("共 ").append(suggestions.size()).append(" 位成员提出建议\n");
            suggestions.forEach(s -> content.append("- ").append(s).append("\n"));
        } else {
            content.append("本次无成员提出建议。\n");
        }

        return content.toString();
    }

    /**
     * 保存汇编并返回 VO
     */
    private CompilationVO saveCompilation(CheckinTask task, Long activityId, String content) {
        LambdaQueryWrapper<CheckinRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(CheckinRecord::getTaskId, task.getId());
        List<CheckinRecord> records = recordMapper.selectList(recordWrapper);

        double avgRating = records.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(CheckinRecord::getRating)
                .average()
                .orElse(0.0);

        List<String> problems = records.stream()
                .map(CheckinRecord::getProblem)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toList());
        List<String> suggestions = records.stream()
                .map(CheckinRecord::getSuggestion)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());

        Compilation compilation = new Compilation();
        compilation.setTaskId(task.getId());
        compilation.setActivityId(activityId);
        compilation.setClubId(task.getClubId());
        compilation.setContent(content);
        compilation.setMemberCount(records.size());
        compilation.setAvgRating(BigDecimal.valueOf(avgRating).setScale(1, RoundingMode.HALF_UP));
        compilation.setProblemSummary(problems.isEmpty() ? "" : "共 " + problems.size() + " 位成员提出问题");
        compilation.setSuggestionSummary(suggestions.isEmpty() ? "" : "共 " + suggestions.size() + " 位成员提出建议");
        compilation.setIsIngested(Compilation.NOT_INGESTED);
        compilationMapper.insert(compilation);

        CompilationVO vo = new CompilationVO();
        vo.setId(compilation.getId());
        vo.setActivityId(compilation.getActivityId());
        vo.setContent(compilation.getContent());
        vo.setMemberCount(compilation.getMemberCount());
        vo.setAvgRating(compilation.getAvgRating());
        vo.setProblemSummary(compilation.getProblemSummary());
        vo.setSuggestionSummary(compilation.getSuggestionSummary());
        vo.setIsIngested(false);
        vo.setCreatedAt(compilation.getCreatedAt());
        return vo;
    }

    @Override
    public CompilationVO getCompilation(Long activityId) {
        LambdaQueryWrapper<CheckinTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CheckinTask::getActivityId, activityId);
        CheckinTask task = taskMapper.selectOne(taskWrapper);
        if (task == null) {
            return null;
        }

        LambdaQueryWrapper<Compilation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Compilation::getTaskId, task.getId());
        Compilation compilation = compilationMapper.selectOne(wrapper);
        if (compilation == null) {
            return null;
        }

        CompilationVO vo = new CompilationVO();
        vo.setId(compilation.getId());
        vo.setActivityId(compilation.getActivityId());
        vo.setContent(compilation.getContent());
        vo.setFilePath(compilation.getFilePath());
        vo.setMemberCount(compilation.getMemberCount());
        vo.setAvgRating(compilation.getAvgRating());
        vo.setProblemSummary(compilation.getProblemSummary());
        vo.setSuggestionSummary(compilation.getSuggestionSummary());
        vo.setIsIngested(compilation.getIsIngested() == Compilation.INGESTED);
        vo.setCreatedAt(compilation.getCreatedAt());
        return vo;
    }

    private PointsLog buildPointsLog(Long clubId, Long userId, int points, String reason,
                                  String description, Long activityId, Long checkinId) {
        PointsLog log = new PointsLog();
        log.setClubId(clubId);
        log.setUserId(userId);
        log.setPoints(points);
        log.setReason(reason);
        log.setDescription(description);
        log.setRelatedActivityId(activityId);
        log.setRelatedCheckinId(checkinId);
        return log;
    }

    private CheckinRecordVO toRecordVO(CheckinRecord record) {
        CheckinRecordVO vo = new CheckinRecordVO();
        vo.setId(record.getId());
        vo.setActivityId(record.getActivityId());
        vo.setUserId(record.getUserId());
        vo.setRating(record.getRating());
        vo.setReflection(record.getReflection());
        vo.setProblem(record.getProblem());
        vo.setSuggestion(record.getSuggestion());
        vo.setIsAdopted(record.getIsAdopted() == CheckinRecord.ADOPTED);
        vo.setCreatedAt(record.getCreatedAt());

        try {
            vo.setPhotoUrls(objectMapper.readValue(record.getPhotoUrls() != null ? record.getPhotoUrls() : "[]",
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            vo.setFileUrls(objectMapper.readValue(record.getFileUrls() != null ? record.getFileUrls() : "[]",
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        } catch (JsonProcessingException e) {
            vo.setPhotoUrls(new ArrayList<>());
            vo.setFileUrls(new ArrayList<>());
        }

        return vo;
    }
}
