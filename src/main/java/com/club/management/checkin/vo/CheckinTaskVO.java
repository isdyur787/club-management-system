package com.club.management.checkin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CheckinTaskVO {

    private Long id;

    private Long activityId;

    private String title;

    private String description;

    private Boolean requireReflection;

    private Boolean requirePhoto;

    private Boolean requireSuggestion;

    private LocalDateTime deadline;

    private Integer status;

    private Integer submittedCount;

    private Integer requireCount;

    private Double avgRating;
}
