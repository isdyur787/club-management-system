package com.club.management.points.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointsLogVO {

    private Long id;

    private Integer points;

    private String reason;

    private String description;

    private Long relatedActivityId;

    private LocalDateTime createdAt;
}
