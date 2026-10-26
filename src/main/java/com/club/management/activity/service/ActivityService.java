package com.club.management.activity.service;

import com.club.management.activity.dto.*;
import com.club.management.activity.vo.ActivityDetailVO;
import com.club.management.activity.vo.ActivityVO;
import com.club.management.common.response.PageResult;

public interface ActivityService {

    ActivityVO createActivity(Long userId, ActivityCreateDTO dto);

    PageResult<ActivityVO> listActivities(ActivityQueryDTO dto);

    ActivityDetailVO getActivityDetail(Long id);

    void updateActivity(Long id, ActivityUpdateDTO dto);

    void deleteActivity(Long id);

    void submitForApproval(Long id);

    void approveActivity(Long id, Long approverId, ApprovalDTO dto);

    void startActivity(Long id);

    void completeActivity(Long id, CompleteDTO dto);
}
