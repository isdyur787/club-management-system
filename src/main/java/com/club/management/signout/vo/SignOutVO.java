package com.club.management.signout.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SignOutVO {

    private Long activityId;

    private LocalDateTime signedOutAt;
}
