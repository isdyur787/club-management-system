package com.club.management.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_approval")
public class Approval extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long approverId;

    private Integer approvalLevel;

    private Integer result;

    private String comment;

    private LocalDateTime approvedAt;
}
