package com.club.management.club.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClubDetailVO {

    private Long id;
    private String clubName;
    private String category;
    private String description;
    private String charter;
    private String coverImage;
    private PresidentVO president;
    private Integer memberCount;
    private Integer approvalStatus;
    private LocalDateTime createdAt;

    @Data
    public static class PresidentVO {
        private Long id;
        private String realName;
    }
}
