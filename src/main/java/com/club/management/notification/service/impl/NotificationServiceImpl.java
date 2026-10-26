package com.club.management.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.response.PageResult;
import com.club.management.notification.dto.NotificationQueryDTO;
import com.club.management.notification.dto.SendNotificationDTO;
import com.club.management.notification.entity.Notification;
import com.club.management.notification.mapper.NotificationMapper;
import com.club.management.notification.service.NotificationService;
import com.club.management.notification.vo.NotificationVO;
import com.club.management.notification.vo.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public PageResult<NotificationVO> myNotifications(Long userId, NotificationQueryDTO dto) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (StringUtils.hasText(dto.getType())) {
            wrapper.eq(Notification::getType, dto.getType());
        }
        if (dto.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, dto.getIsRead());
        }
        wrapper.orderByDesc(Notification::getCreatedAt);

        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<NotificationVO> voList = page.getRecords().stream().map(noti -> {
            NotificationVO vo = new NotificationVO();
            vo.setId(noti.getId());
            vo.setTitle(noti.getTitle());
            vo.setContent(noti.getContent());
            vo.setType(noti.getType());
            vo.setRelatedId(noti.getRelatedId());
            vo.setIsRead(noti.getIsRead());
            vo.setCreatedAt(noti.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        notification.setIsRead(Notification.READ);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, Notification.UNREAD);

        Notification update = new Notification();
        update.setIsRead(Notification.READ);
        update.setReadAt(LocalDateTime.now());
        notificationMapper.update(update, wrapper);
    }

    @Override
    public UnreadCountVO getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, Notification.UNREAD);
        long count = notificationMapper.selectCount(wrapper);

        UnreadCountVO vo = new UnreadCountVO();
        vo.setCount((int) count);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification(SendNotificationDTO dto) {
        Notification notification = new Notification();
        notification.setUserId(dto.getUserId());
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setType(dto.getType());
        notification.setRelatedId(dto.getRelatedId());
        notification.setIsRead(Notification.UNREAD);
        notificationMapper.insert(notification);
    }
}
