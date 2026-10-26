package com.club.management.registration.service;

import com.club.management.common.response.PageResult;
import com.club.management.registration.dto.RegistrationDTO;
import com.club.management.registration.dto.RegistrationQueryDTO;
import com.club.management.registration.dto.SignInDTO;
import com.club.management.registration.vo.RegistrationVO;
import com.club.management.registration.vo.SignInCodeVO;
import com.club.management.registration.vo.SignInStatisticsVO;

public interface RegistrationService {

    RegistrationVO register(Long userId, RegistrationDTO dto);

    void cancelRegistration(Long activityId, Long userId);

    PageResult<RegistrationVO> myRegistrations(Long userId, RegistrationQueryDTO dto);

    PageResult<RegistrationVO> listRegistrations(Long activityId, RegistrationQueryDTO dto);

    SignInCodeVO generateSignInCode(Long activityId);

    void signIn(Long userId, SignInDTO dto);

    SignInStatisticsVO getSignInStatistics(Long activityId);
}
