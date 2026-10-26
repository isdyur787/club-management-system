package com.club.management.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SignInCodeVO {

    private String signInCode;
    private LocalDateTime expireAt;
}
