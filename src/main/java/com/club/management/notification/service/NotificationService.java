package com.club.management.notification.service;

import com.club.management.common.response.PageResult;
import com.club.management.notification.dto.NotificationQueryDTO;
import com.club.management.notification.dto.SendNotificationDTO;
import com.club.management.notification.vo.NotificationVO;
import com.club.management.notification.vo.UnreadCountVO;

public interface NotificationService {

    PageResult<NotificationVO> myNotifications(Long userId, NotificationQueryDTO dto);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);

    UnreadCountVO getUnreadCount(Long userId);

    void sendNotification(SendNotificationDTO dto);
}
