package com.club.management.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_activity_report")
public class ActivityReport extends BaseEntity {

    public static final int INGESTED = 1;
    public static final int NOT_INGESTED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long clubId;

    private String title;

    private String content;

    private String filePath;

    private BigDecimal avgRating;

    private Integer participantCount;

    private Integer checkinCount;

    private String status;

    private String rejectReason;

    private Integer revisionCount;

    private Long confirmedBy;

    private LocalDateTime confirmedAt;

    private Integer isIngested;

    private LocalDateTime ingestedAt;
}
