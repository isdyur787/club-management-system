package com.club.management.checkin.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckinRecordVO {

    private Long id;

    private Long activityId;

    private Long userId;

    private String realName;

    private Integer rating;

    private String reflection;

    private String problem;

    private String suggestion;

    private List<String> photoUrls;

    private List<String> fileUrls;

    private Boolean isAdopted;

    private LocalDateTime createdAt;
}
