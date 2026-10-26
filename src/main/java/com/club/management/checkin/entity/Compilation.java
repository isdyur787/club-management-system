package com.club.management.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_checkin_compilation")
public class Compilation extends BaseEntity {

    public static final int INGESTED = 1;
    public static final int NOT_INGESTED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long activityId;

    private Long clubId;

    private String content;

    private String filePath;

    private Integer memberCount;

    private BigDecimal avgRating;

    private String problemSummary;

    private String suggestionSummary;

    private String chromaDocIds;

    private Integer isIngested;

    private LocalDateTime ingestedAt;
}
