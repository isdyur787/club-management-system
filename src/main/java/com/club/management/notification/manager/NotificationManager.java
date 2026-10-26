package com.club.management.notification.manager;

import com.club.management.notification.entity.Notification;
import com.club.management.notification.mapper.NotificationMapper;
import com.club.management.common.enums.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationManager {

    @Autowired
    private NotificationMapper notificationMapper;

    /**
     * 通用通知发送入口（模板方法）
     */
    public final void send(Long userId, String title, Long relatedId) {
        String content = buildContent(userId, title, relatedId);
        String channel = selectChannel(userId);
        doSend(userId, title, content, channel, relatedId);
    }

    /**
     * 构建通知内容，子类可覆写以定制
     */
    protected String buildContent(Long userId, String title, Long relatedId) {
        return title;
    }

    /**
     * 选择通知渠道，子类可覆写
     */
    protected String selectChannel(Long userId) {
        return NotificationType.SYSTEM.name();
    }

    private void doSend(Long userId, String title, String content, String channel, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(channel);
        notification.setRelatedId(relatedId);
        notification.setIsRead(Notification.UNREAD);
        notificationMapper.insert(notification);
    }

    /**
     * 发送系统通知
     */
    public void sendSystemNotification(Long userId, String title, String content, Long relatedId) {
        doSend(userId, title, content, NotificationType.SYSTEM.name(), relatedId);
    }

    /**
     * 发送活动通知
     */
    public void sendActivityNotification(Long userId, String title, String content, Long relatedId) {
        doSend(userId, title, content, NotificationType.ACTIVITY.name(), relatedId);
    }

    /**
     * 发送审批通知
     */
    public void sendApprovalNotification(Long userId, String title, String content, Long relatedId) {
        doSend(userId, title, content, NotificationType.APPROVAL.name(), relatedId);
    }
}
