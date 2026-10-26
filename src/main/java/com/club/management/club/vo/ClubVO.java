package com.club.management.club.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClubVO {

    private Long id;
    private String clubName;
    private String category;
    private String description;
    private String presidentName;
    private Integer memberCount;
    private Integer approvalStatus;
    private LocalDateTime createdAt;
}
