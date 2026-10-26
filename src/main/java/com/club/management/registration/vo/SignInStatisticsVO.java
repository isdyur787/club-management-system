package com.club.management.registration.vo;

import lombok.Data;

@Data
public class SignInStatisticsVO {

    private Integer totalRegistered;
    private Integer totalSignedIn;
    private Double signInRate;
}
