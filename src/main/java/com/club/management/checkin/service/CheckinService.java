package com.club.management.checkin.service;

import com.club.management.checkin.dto.CheckinRecordSubmitDTO;
import com.club.management.checkin.dto.CheckinTaskCreateDTO;
import com.club.management.checkin.vo.CheckinRecordVO;
import com.club.management.checkin.vo.CheckinTaskVO;
import com.club.management.checkin.vo.CompilationVO;
import com.club.management.common.response.PageResult;

import java.util.List;
import java.util.Map;

public interface CheckinService {

    CheckinTaskVO createTask(CheckinTaskCreateDTO dto);

    CheckinTaskVO getTaskByActivityId(Long activityId);

    CheckinRecordVO submitRecord(CheckinRecordSubmitDTO dto);

    PageResult<CheckinRecordVO> getRecordsByActivityId(Long activityId, int pageNum, int pageSize);

    CheckinRecordVO getMyRecord(Long activityId);

    CheckinRecordVO adoptSuggestion(Long id);

    Map<String, Object> settlePoints(Long activityId);

    CompilationVO generateCompilation(Long activityId);

    CompilationVO getCompilation(Long activityId);
}
