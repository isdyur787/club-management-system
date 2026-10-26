package com.club.management.club.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberVO {

    private Long userId;
    private String realName;
    private String role;
    private LocalDateTime joinedAt;
}
