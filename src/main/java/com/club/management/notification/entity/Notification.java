package com.club.management.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("noti_notification")
public class Notification extends BaseEntity {

    public static final int READ = 1;
    public static final int UNREAD = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    private String type;

    private Long relatedId;

    private Integer isRead;

    private LocalDateTime readAt;
}
