package com.club.management.registration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.club.entity.ClubInfo;
import com.club.management.club.mapper.ClubInfoMapper;
import com.club.management.common.config.RedisKeyConstants;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.enums.ActivityStatus;
import com.club.management.common.event.RegistrationFullEvent;
import com.club.management.common.exception.*;
import com.club.management.common.response.PageResult;
import com.club.management.common.util.SecurityUtil;
import com.club.management.registration.dto.RegistrationDTO;
import com.club.management.registration.dto.RegistrationQueryDTO;
import com.club.management.registration.dto.SignInDTO;
import com.club.management.registration.entity.Registration;
import com.club.management.registration.entity.SignIn;
import com.club.management.registration.mapper.RegistrationMapper;
import com.club.management.registration.mapper.SignInMapper;
import com.club.management.registration.service.RegistrationService;
import com.club.management.registration.vo.RegistrationVO;
import com.club.management.registration.vo.SignInCodeVO;
import com.club.management.registration.vo.SignInStatisticsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 报名签到服务实现，通过事务与活动版本号保护人数更新，使用 Redis 短期签到码和集合成员记录辅助签到校验与防重。
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    @Autowired
    private RegistrationMapper registrationMapper;

    @Autowired
    private SignInMapper signInMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private ClubInfoMapper clubInfoMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationVO register(Long userId, RegistrationDTO dto) {
        // 查询活动
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 检查活动状态
        if (!ActivityStatus.APPROVED.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动状态不允许报名");
        }

        // 检查报名截止时间
        if (activity.getRegistrationDeadline() != null
                && LocalDateTime.now().isAfter(activity.getRegistrationDeadline())) {
            throw new BusinessException(ErrorCode.REGISTRATION_CLOSED);
        }

        // 检查是否已报名
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getActivityId, dto.getActivityId())
                .eq(Registration::getUserId, userId);
        if (registrationMapper.selectCount(wrapper) > 0) {
            throw new ConflictException(ErrorCode.REGISTRATION_DUPLICATE);
        }

        // 把读取到的 version 放进 UPDATE 条件，并在同一条 SQL 中检查人数上限。
        // 其他请求若已修改该活动，本次版本匹配失败，不会覆盖它的报名人数。
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Activity> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", activity.getId())
                .eq("version", activity.getVersion())
                .apply("current_participants < max_participants")
                .set("current_participants", activity.getCurrentParticipants() + 1)
                .set("version", activity.getVersion() + 1);
        int updated = activityMapper.update(null, updateWrapper);

        if (updated == 0) {
            throw new BusinessException(ErrorCode.REGISTRATION_FULL);
        }

        // 人数更新与报名记录位于同一个数据库事务；插入失败时，前面增加的人数也会回滚。
        Registration registration = new Registration();
        registration.setActivityId(dto.getActivityId());
        registration.setUserId(userId);
        registration.setStatus(ActiveStatus.ACTIVE.getCode());
        registration.setRegisteredAt(LocalDateTime.now());
        registrationMapper.insert(registration);

        // 检查是否报名已满
        Activity updatedActivity = activityMapper.selectById(activity.getId());
        if (updatedActivity.getCurrentParticipants() >= updatedActivity.getMaxParticipants()) {
            eventPublisher.publishEvent(new RegistrationFullEvent(
                    this, activity.getId(), activity.getMaxParticipants()));
        }

        RegistrationVO vo = new RegistrationVO();
        vo.setRegistrationId(registration.getId());
        vo.setActivityId(registration.getActivityId());
        vo.setStatus(registration.getStatus());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRegistration(Long activityId, Long userId) {
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getActivityId, activityId)
                .eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, ActiveStatus.ACTIVE.getCode());
        Registration registration = registrationMapper.selectOne(wrapper);

        if (registration == null) {
            throw new NotFoundException(ErrorCode.REGISTRATION_NOT_FOUND);
        }

        // 检查活动是否已开始
        Activity activity = activityMapper.selectById(activityId);
        if (activity != null && LocalDateTime.now().isAfter(activity.getStartTime())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动已开始，不可取消报名");
        }

        // 更新报名状态
        registration.setStatus(ActiveStatus.INACTIVE.getCode());
        registrationMapper.updateById(registration);

        // 乐观锁减少活动报名人数
        if (activity != null) {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Activity> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.eq("id", activity.getId())
                    .eq("version", activity.getVersion())
                    .apply("current_participants > 0")
                    .setSql("current_participants = current_participants - 1")
                    .set("version", activity.getVersion() + 1);
            int updated = activityMapper.update(null, updateWrapper);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "取消报名失败，请重试");
            }
        }
    }

    @Override
    public PageResult<RegistrationVO> myRegistrations(Long userId, RegistrationQueryDTO dto) {
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getUserId, userId);
        if (dto.getStatus() != null) {
            wrapper.eq(Registration::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(Registration::getRegisteredAt);

        Page<Registration> page = registrationMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        // 批量查询活动和社团，替代 N+1
        List<Registration> records = page.getRecords();
        Set<Long> activityIds = records.stream().map(Registration::getActivityId).collect(Collectors.toSet());
        Map<Long, Activity> activityMap = new java.util.HashMap<>();
        Map<Long, ClubInfo> clubMap = new java.util.HashMap<>();
        if (!activityIds.isEmpty()) {
            List<Activity> activities = activityMapper.selectRegisteredActivities(userId, activityIds);
            activities.forEach(a -> activityMap.put(a.getId(), a));
            Set<Long> clubIds = activities.stream().map(Activity::getClubId).collect(Collectors.toSet());
            if (!clubIds.isEmpty()) {
                clubInfoMapper.selectBatchIds(clubIds).forEach(c -> clubMap.put(c.getId(), c));
            }
        }

        List<RegistrationVO> voList = records.stream().map(reg -> {
            RegistrationVO vo = new RegistrationVO();
            vo.setRegistrationId(reg.getId());
            vo.setActivityId(reg.getActivityId());
            vo.setStatus(reg.getStatus());
            vo.setRegisteredAt(reg.getRegisteredAt() != null ? reg.getRegisteredAt().toString() : null);

            Activity activity = activityMap.get(reg.getActivityId());
            if (activity != null) {
                vo.setActivityTitle(activity.getTitle());
                vo.setStartTime(activity.getStartTime() != null ? activity.getStartTime().toString() : null);
                vo.setLocation(activity.getLocation());
                ClubInfo club = clubMap.get(activity.getClubId());
                if (club != null) {
                    vo.setClubName(club.getClubName());
                }
            }
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    public PageResult<RegistrationVO> listRegistrations(Long activityId, RegistrationQueryDTO dto) {
        LambdaQueryWrapper<Registration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Registration::getActivityId, activityId);
        if (dto.getStatus() != null) {
            wrapper.eq(Registration::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(Registration::getRegisteredAt);

        Page<Registration> page = registrationMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<RegistrationVO> voList = page.getRecords().stream().map(reg -> {
            RegistrationVO vo = new RegistrationVO();
            vo.setRegistrationId(reg.getId());
            vo.setActivityId(reg.getActivityId());
            vo.setStatus(reg.getStatus());
            vo.setRegisteredAt(reg.getRegisteredAt() != null ? reg.getRegisteredAt().toString() : null);
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    public SignInCodeVO generateSignInCode(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 权限校验：调用者必须是活动创建者或社团负责人
        Long currentUserId = SecurityUtil.getCurrentUserId();
        boolean isCreator = activity.getCreatedBy() != null
                && activity.getCreatedBy().equals(currentUserId);
        if (!isCreator) {
            ClubInfo club = clubInfoMapper.selectById(activity.getClubId());
            boolean isClubPresident = club != null && club.getPresidentId() != null
                    && club.getPresidentId().equals(currentUserId);
            if (!isClubPresident) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }
        }

        // 生成6位随机签到码
        String code = generateRandomCode(6);

        // 存储到 Redis，5分钟过期
        redisTemplate.opsForValue().set(RedisKeyConstants.SIGN_IN_CODE + activityId, code, 5, TimeUnit.MINUTES);

        SignInCodeVO vo = new SignInCodeVO();
        vo.setSignInCode(code);
        vo.setExpireAt(LocalDateTime.now().plusMinutes(5));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signIn(Long userId, SignInDTO dto) {
        // 查询活动
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // 检查活动状态
        if (!ActivityStatus.IN_PROGRESS.name().equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动未进行中");
        }

        // 校验签到码
        String cachedCode = redisTemplate.opsForValue().get(RedisKeyConstants.SIGN_IN_CODE + dto.getActivityId());
        if (cachedCode == null || !cachedCode.equals(dto.getSignInCode())) {
            throw new BusinessException(ErrorCode.SIGN_IN_CODE_INVALID);
        }

        // 检查是否已报名
        LambdaQueryWrapper<Registration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(Registration::getActivityId, dto.getActivityId())
                .eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, ActiveStatus.ACTIVE.getCode());
        if (registrationMapper.selectCount(regWrapper) == 0) {
            throw new NotFoundException(ErrorCode.REGISTRATION_NOT_FOUND);
        }

        // Redis SADD 原子地添加用户；返回 0 表示此用户已在签到集合中，无需先查再写。
        String signKey = RedisKeyConstants.SIGN_IN + dto.getActivityId();
        Long added = redisTemplate.opsForSet().add(signKey, String.valueOf(userId));
        if (added == null || added == 0) {
            throw new ConflictException(ErrorCode.SIGN_IN_DUPLICATE);
        }

        try {
            // 创建签到记录
            SignIn signIn = new SignIn();
            signIn.setActivityId(dto.getActivityId());
            signIn.setUserId(userId);
            signIn.setSignInCode(dto.getSignInCode());
            signIn.setSignedAt(LocalDateTime.now());
            signInMapper.insert(signIn);
        } catch (Exception e) {
            // Redis 不参加数据库事务，这里对插入阶段的异常手动移除防重标记，允许用户重试。
            redisTemplate.opsForSet().remove(signKey, String.valueOf(userId));
            throw e;
        }
    }

    @Override
    public SignInStatisticsVO getSignInStatistics(Long activityId) {
        // 查询报名总数
        LambdaQueryWrapper<Registration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(Registration::getActivityId, activityId)
                .eq(Registration::getStatus, ActiveStatus.ACTIVE.getCode());
        int totalRegistered = Math.toIntExact(registrationMapper.selectCount(regWrapper));

        // 查询签到总数
        LambdaQueryWrapper<SignIn> signInWrapper = new LambdaQueryWrapper<>();
        signInWrapper.eq(SignIn::getActivityId, activityId);
        int totalSignedIn = Math.toIntExact(signInMapper.selectCount(signInWrapper));

        SignInStatisticsVO vo = new SignInStatisticsVO();
        vo.setTotalRegistered(totalRegistered);
        vo.setTotalSignedIn(totalSignedIn);
        vo.setSignInRate(totalRegistered > 0 ? (double) totalSignedIn / totalRegistered : 0.0);
        return vo;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
