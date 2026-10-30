package com.club.management.notification.listener;

import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.common.event.*;
import com.club.management.common.enums.NotificationType;
import com.club.management.notification.manager.NotificationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 活动事件的异步订阅者，将审批通过/驳回、活动结束及报名已满事件转为发给活动创建者的站内通知。
 * 异步方法在另一条线程处理，普通身份/租户 ThreadLocal 不会自动传递。
 * 当前使用普通 @EventListener，执行时机不保证在发布方数据库事务提交之后。
 */
@Component
public class ActivityEventListener {

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private NotificationManager notificationManager;

    @Async
    @EventListener
    public void onActivityApproved(ActivityApprovedEvent event) {
        Activity activity = activityMapper.selectById(event.getActivityId());
        if (activity != null) {
            sendNotification(activity.getCreatedBy(),
                    "活动审批通过",
                    "您创建的活动「" + activity.getTitle() + "」已通过审批",
                    NotificationType.APPROVAL,
                    event.getActivityId());
        }
    }

    @Async
    @EventListener
    public void onActivityRejected(ActivityRejectedEvent event) {
        Activity activity = activityMapper.selectById(event.getActivityId());
        if (activity != null) {
            sendNotification(activity.getCreatedBy(),
                    "活动审批驳回",
                    "您创建的活动「" + activity.getTitle() + "」审批被驳回，原因：" + event.getReason(),
                    NotificationType.APPROVAL,
                    event.getActivityId());
        }
    }

    @Async
    @EventListener
    public void onActivityCompleted(ActivityCompletedEvent event) {
        // 通知创建者
        Activity activity = activityMapper.selectById(event.getActivityId());
        if (activity != null) {
            sendNotification(activity.getCreatedBy(),
                    "活动已结束",
                    "活动「" + activity.getTitle() + "」已结束，请提交活动总结",
                    NotificationType.ACTIVITY,
                    event.getActivityId());
        }
    }

    @Async
    @EventListener
    public void onRegistrationFull(RegistrationFullEvent event) {
        Activity activity = activityMapper.selectById(event.getActivityId());
        if (activity != null) {
            sendNotification(activity.getCreatedBy(),
                    "报名已满",
                    "活动「" + activity.getTitle() + "」报名人数已达上限",
                    NotificationType.ACTIVITY,
                    event.getActivityId());
        }
    }

    private void sendNotification(Long userId, String title, String content,
                                   NotificationType type, Long relatedId) {
        switch (type) {
            case APPROVAL -> notificationManager.sendApprovalNotification(userId, title, content, relatedId);
            case ACTIVITY -> notificationManager.sendActivityNotification(userId, title, content, relatedId);
            default -> notificationManager.sendSystemNotification(userId, title, content, relatedId);
        }
    }
}
