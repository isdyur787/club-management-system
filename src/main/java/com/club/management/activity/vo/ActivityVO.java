package com.club.management.activity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityVO {

    private Long id;
    private String title;
    private Long clubId;
    private String clubName;
    private String type;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal budget;
    private String status;
    private String coverImage;
    private Boolean needRegistration;
    private LocalDateTime createdAt;
}
