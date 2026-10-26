package com.club.management.registration.vo;

import lombok.Data;

@Data
public class RegistrationVO {

    private Long registrationId;
    private Long activityId;
    private String activityTitle;
    private String clubName;
    private String startTime;
    private String location;
    private Integer status;
    private String registeredAt;
}
