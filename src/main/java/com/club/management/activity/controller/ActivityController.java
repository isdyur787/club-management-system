package com.club.management.activity.controller;

import com.club.management.activity.dto.*;
import com.club.management.activity.service.ActivityService;
import com.club.management.activity.vo.ActivityDetailVO;
import com.club.management.activity.vo.ActivityVO;
import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @PostMapping
    @RequirePermission("activity:create")
    @OperationLog(module = "活动管理", operation = "创建活动")
    public R<ActivityVO> createActivity(@Valid @RequestBody ActivityCreateDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        ActivityVO vo = activityService.createActivity(userId, dto);
        return R.ok(vo);
    }

    @GetMapping
    public R<PageResult<ActivityVO>> listActivities(ActivityQueryDTO dto) {
        PageResult<ActivityVO> result = activityService.listActivities(dto);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<ActivityDetailVO> getActivityDetail(@PathVariable Long id) {
        ActivityDetailVO vo = activityService.getActivityDetail(id);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("activity:edit")
    @OperationLog(module = "活动管理", operation = "修改活动")
    public R<Void> updateActivity(@PathVariable Long id, @Valid @RequestBody ActivityUpdateDTO dto) {
        activityService.updateActivity(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("activity:delete")
    @OperationLog(module = "活动管理", operation = "删除活动")
    public R<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequirePermission("activity:edit")
    @OperationLog(module = "活动管理", operation = "提交活动审批")
    public R<Void> submitForApproval(@PathVariable Long id) {
        activityService.submitForApproval(id);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("activity:approve")
    @OperationLog(module = "活动管理", operation = "审批活动")
    public R<Void> approveActivity(@PathVariable Long id, @Valid @RequestBody ApprovalDTO dto) {
        Long approverId = SecurityUtil.getCurrentUserId();
        activityService.approveActivity(id, approverId, dto);
        return R.ok();
    }

    @PutMapping("/{id}/status/in-progress")
    @RequirePermission("activity:edit")
    @OperationLog(module = "活动管理", operation = "开始活动")
    public R<Void> startActivity(@PathVariable Long id) {
        activityService.startActivity(id);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    @RequirePermission("activity:edit")
    @OperationLog(module = "活动管理", operation = "结束活动")
    public R<Void> completeActivity(@PathVariable Long id, @Valid @RequestBody CompleteDTO dto) {
        activityService.completeActivity(id, dto);
        return R.ok();
    }
}
