package com.club.management.notification.controller;

import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import com.club.management.notification.dto.NotificationQueryDTO;
import com.club.management.notification.dto.SendNotificationDTO;
import com.club.management.notification.service.NotificationService;
import com.club.management.notification.vo.NotificationVO;
import com.club.management.notification.vo.UnreadCountVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/me")
    public R<PageResult<NotificationVO>> myNotifications(NotificationQueryDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        PageResult<NotificationVO> result = notificationService.myNotifications(userId, dto);
        return R.ok(result);
    }

    @PutMapping("/{id}/read")
    @OperationLog(module = "通知管理", operation = "标记通知已读")
    public R<Void> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return R.ok();
    }

    @PutMapping("/read-all")
    @OperationLog(module = "通知管理", operation = "批量标记已读")
    public R<Void> markAllAsRead() {
        Long userId = SecurityUtil.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return R.ok();
    }

    @GetMapping("/me/unread-count")
    public R<UnreadCountVO> getUnreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();
        UnreadCountVO vo = notificationService.getUnreadCount(userId);
        return R.ok(vo);
    }

    @PostMapping
    @RequirePermission("notification:send")
    @OperationLog(module = "通知管理", operation = "发送通知")
    public R<Void> sendNotification(@Valid @RequestBody SendNotificationDTO dto) {
        notificationService.sendNotification(dto);
        return R.ok();
    }
}
