package com.club.management.signout.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.common.config.PointsProperties;
import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.util.SecurityUtil;
import com.club.management.points.entity.PointsLog;
import com.club.management.points.mapper.PointsLogMapper;
import com.club.management.registration.entity.Registration;
import com.club.management.registration.mapper.RegistrationMapper;
import com.club.management.signout.dto.SignOutDTO;
import com.club.management.signout.entity.SignOut;
import com.club.management.signout.mapper.SignOutMapper;
import com.club.management.signout.service.SignOutService;
import com.club.management.signout.vo.SignOutVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SignOutServiceImpl implements SignOutService {

    @Autowired
    private SignOutMapper signOutMapper;

    @Autowired
    private RegistrationMapper registrationMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Autowired
    private PointsProperties pointsProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignOutVO signOut(SignOutDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 校验已签到
        LambdaQueryWrapper<Registration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(Registration::getActivityId, dto.getActivityId())
                .eq(Registration::getUserId, userId);
        Registration registration = registrationMapper.selectOne(regWrapper);
        if (registration == null) {
            throw new BusinessException(ErrorCode.SIGNOUT_NOT_CHECKED_IN);
        }

        // 校验未签退
        LambdaQueryWrapper<SignOut> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(SignOut::getActivityId, dto.getActivityId())
                .eq(SignOut::getUserId, userId);
        if (signOutMapper.selectCount(existingWrapper) > 0) {
            throw new BusinessException(ErrorCode.SIGNOUT_ALREADY_DONE);
        }

        // 创建签退记录
        SignOut signOut = new SignOut();
        signOut.setActivityId(dto.getActivityId());
        signOut.setUserId(userId);
        signOut.setSignedOutAt(LocalDateTime.now());
        signOutMapper.insert(signOut);

        // 积分 +1（从活动获取 clubId）
        Activity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        PointsLog pointsLog = new PointsLog();
        pointsLog.setClubId(activity.getClubId());
        pointsLog.setUserId(userId);
        pointsLog.setPoints(pointsProperties.getSignOut());
        pointsLog.setReason("SIGN_OUT");
        pointsLog.setDescription("签退成功");
        pointsLog.setRelatedActivityId(dto.getActivityId());
        pointsLogMapper.insert(pointsLog);

        SignOutVO vo = new SignOutVO();
        vo.setActivityId(signOut.getActivityId());
        vo.setSignedOutAt(signOut.getSignedOutAt());
        return vo;
    }
}
