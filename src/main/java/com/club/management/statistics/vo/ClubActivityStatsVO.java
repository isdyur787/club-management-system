package com.club.management.statistics.vo;

import lombok.Data;

import java.util.List;

@Data
public class ClubActivityStatsVO {

    private Long clubId;
    private String clubName;
    private Integer year;
    private Integer totalActivities;
    private List<MonthlyStat> monthlyStats;
    private Integer totalParticipants;
    private Integer averageParticipants;

    @Data
    public static class MonthlyStat {
        private Integer month;
        private Integer activityCount;
        private Integer participantCount;
    }
}
