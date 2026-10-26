package com.club.management.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_checkin_task")
public class CheckinTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long clubId;

    private String title;

    private String description;

    private Integer requireReflection;

    private Integer requirePhoto;

    private Integer requireSuggestion;

    private LocalDateTime deadline;

    private Integer status;
}
