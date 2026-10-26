package com.club.management.checkin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CompilationVO {

    private Long id;

    private Long activityId;

    private String content;

    private String filePath;

    private Integer memberCount;

    private BigDecimal avgRating;

    private String problemSummary;

    private String suggestionSummary;

    private Boolean isIngested;

    private LocalDateTime createdAt;
}
