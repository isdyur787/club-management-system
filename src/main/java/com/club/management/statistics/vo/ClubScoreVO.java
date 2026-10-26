package com.club.management.statistics.vo;

import lombok.Data;

@Data
public class ClubScoreVO {

    private Long clubId;
    private String clubName;
    private Double activityScore;
    private Double participationScore;
    private Double totalScore;
    private Integer rank;
}
