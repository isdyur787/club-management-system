package com.club.management.registration.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reg_registration")
public class Registration extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    private Integer status;

    private LocalDateTime registeredAt;
}
