package com.club.management.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReportDraftVO {

    private Long id;

    private Long activityId;

    private String title;

    private String content;

    private String filePath;

    private BigDecimal avgRating;

    private Integer participantCount;

    private Integer checkinCount;

    private String status;

    private Integer revisionCount;

    private LocalDateTime createdAt;
}
