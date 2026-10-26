package com.club.management.checkin.controller;

import com.club.management.checkin.dto.CheckinRecordSubmitDTO;
import com.club.management.checkin.dto.CheckinTaskCreateDTO;
import com.club.management.checkin.service.CheckinService;
import com.club.management.checkin.vo.CheckinRecordVO;
import com.club.management.checkin.vo.CheckinTaskVO;
import com.club.management.checkin.vo.CompilationVO;
import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    @PostMapping("/tasks")
    @RequirePermission("checkin:manage")
    @OperationLog(module = "打卡", operation = "创建打卡任务")
    public R<CheckinTaskVO> createTask(@RequestBody @Valid CheckinTaskCreateDTO dto) {
        return R.ok(checkinService.createTask(dto));
    }

    @GetMapping("/tasks/{activityId}")
    @RequirePermission("checkin:manage")
    public R<CheckinTaskVO> getTask(@PathVariable Long activityId) {
        return R.ok(checkinService.getTaskByActivityId(activityId));
    }

    @PostMapping("/records")
    @OperationLog(module = "打卡", operation = "提交打卡")
    public R<CheckinRecordVO> submitRecord(@RequestBody @Valid CheckinRecordSubmitDTO dto) {
        return R.ok(checkinService.submitRecord(dto));
    }

    @GetMapping("/records/{activityId}")
    @RequirePermission("checkin:manage")
    public R<PageResult<CheckinRecordVO>> getRecords(
            @PathVariable Long activityId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(checkinService.getRecordsByActivityId(activityId, pageNum, pageSize));
    }

    @GetMapping("/my/{activityId}")
    public R<CheckinRecordVO> getMyRecord(@PathVariable Long activityId) {
        return R.ok(checkinService.getMyRecord(activityId));
    }

    @PatchMapping("/records/{id}/adopt")
    @RequirePermission("checkin:manage")
    @OperationLog(module = "打卡", operation = "采纳建议")
    public R<CheckinRecordVO> adoptSuggestion(@PathVariable Long id) {
        return R.ok(checkinService.adoptSuggestion(id));
    }

    @PostMapping("/settle/{activityId}")
    @RequirePermission("checkin:manage")
    @OperationLog(module = "打卡", operation = "积分结算")
    public R<Map<String, Object>> settlePoints(@PathVariable Long activityId) {
        return R.ok(checkinService.settlePoints(activityId));
    }

    @PostMapping("/compilation/generate/{activityId}")
    @RequirePermission("checkin:manage")
    @OperationLog(module = "打卡", operation = "生成汇编文档")
    public R<CompilationVO> generateCompilation(@PathVariable Long activityId) {
        return R.ok(checkinService.generateCompilation(activityId));
    }

    @GetMapping("/compilation/{activityId}")
    @RequirePermission("checkin:view")
    public R<CompilationVO> getCompilation(@PathVariable Long activityId) {
        return R.ok(checkinService.getCompilation(activityId));
    }
}
