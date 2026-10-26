package com.club.management.registration.controller;

import com.club.management.common.annotation.Idempotent;
import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import com.club.management.registration.dto.RegistrationDTO;
import com.club.management.registration.dto.RegistrationQueryDTO;
import com.club.management.registration.dto.SignInDTO;
import com.club.management.registration.service.RegistrationService;
import com.club.management.registration.vo.RegistrationVO;
import com.club.management.registration.vo.SignInCodeVO;
import com.club.management.registration.vo.SignInStatisticsVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @PostMapping("/api/v1/registrations")
    @Idempotent(key = "#dto.activityId + ':' + T(com.club.management.common.util.SecurityUtil).getCurrentUserId()")
    @OperationLog(module = "报名签到", operation = "报名活动")
    public R<RegistrationVO> register(@Valid @RequestBody RegistrationDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        RegistrationVO vo = registrationService.register(userId, dto);
        return R.ok(vo);
    }

    @DeleteMapping("/api/v1/registrations/{activityId}")
    @OperationLog(module = "报名签到", operation = "取消报名")
    public R<Void> cancelRegistration(@PathVariable Long activityId) {
        Long userId = SecurityUtil.getCurrentUserId();
        registrationService.cancelRegistration(activityId, userId);
        return R.ok();
    }

    @GetMapping("/api/v1/registrations/me")
    public R<PageResult<RegistrationVO>> myRegistrations(RegistrationQueryDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        PageResult<RegistrationVO> result = registrationService.myRegistrations(userId, dto);
        return R.ok(result);
    }

    @GetMapping("/api/v1/activities/{activityId}/registrations")
    @RequirePermission("registration:manage")
    public R<PageResult<RegistrationVO>> listRegistrations(
            @PathVariable Long activityId, RegistrationQueryDTO dto) {
        PageResult<RegistrationVO> result = registrationService.listRegistrations(activityId, dto);
        return R.ok(result);
    }

    @PostMapping("/api/v1/activities/{id}/sign-in/code")
    @RequirePermission("signin:manage")
    @OperationLog(module = "报名签到", operation = "生成签到码")
    public R<SignInCodeVO> generateSignInCode(@PathVariable Long id) {
        SignInCodeVO vo = registrationService.generateSignInCode(id);
        return R.ok(vo);
    }

    @PostMapping("/api/v1/sign-in")
    @OperationLog(module = "报名签到", operation = "签到")
    public R<Void> signIn(@Valid @RequestBody SignInDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        registrationService.signIn(userId, dto);
        return R.ok();
    }

    @GetMapping("/api/v1/activities/{id}/sign-in/statistics")
    @RequirePermission("signin:manage")
    public R<SignInStatisticsVO> getSignInStatistics(@PathVariable Long id) {
        SignInStatisticsVO vo = registrationService.getSignInStatistics(id);
        return R.ok(vo);
    }
}
