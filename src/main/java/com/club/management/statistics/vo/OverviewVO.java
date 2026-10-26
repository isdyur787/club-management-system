package com.club.management.statistics.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OverviewVO {

    private Integer totalUsers;
    private Integer totalClubs;
    private Integer totalActivities;
    private Integer totalParticipants;
    private List<RecentActivity> recentActivities;

    @Data
    public static class RecentActivity {
        private Long id;
        private String title;
        private String clubName;
        private String status;
        private LocalDateTime startTime;
    }
}
