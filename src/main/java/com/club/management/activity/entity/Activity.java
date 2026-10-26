package com.club.management.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_activity")
public class Activity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;

    private String title;

    private String description;

    private String type;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime registrationDeadline;

    private Integer maxParticipants;

    private Integer currentParticipants;

    private BigDecimal budget;

    private String status;

    @Version
    private Integer version;

    private String summary;

    private String coverImage;

    private Boolean needRegistration;

    private Boolean needApproval;
}
