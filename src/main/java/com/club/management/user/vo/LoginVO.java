package com.club.management.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
}
