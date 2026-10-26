package com.club.management.points.vo;

import lombok.Data;

@Data
public class PointsRankingVO {

    private Integer rank;

    private Long userId;

    private String realName;

    private Integer totalPoints;

    private String avatar;
}
