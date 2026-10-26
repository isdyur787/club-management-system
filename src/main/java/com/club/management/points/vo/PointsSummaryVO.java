package com.club.management.points.vo;

import lombok.Data;
import java.util.List;

@Data
public class PointsSummaryVO {

    private Integer totalPoints;

    private Integer rank;

    private Integer memberCount;

    private Integer clubTotalPoints;
    private Integer scoredMemberCount;
    private Integer monthPoints;
    private List<DailyPoint> dailyPoints;
    private List<SourcePoint> sourcePoints;

    @Data
    public static class DailyPoint {
        private String date;
        private Integer points;
    }

    @Data
    public static class SourcePoint {
        private String reason;
        private Integer points;
        private Integer entryCount;
    }
}
