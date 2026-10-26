package com.club.management.activity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ActivityDetailVO {

    private Long id;
    private String title;
    private String description;
    private Long clubId;
    private String clubName;
    private String type;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal budget;
    private String status;
    private String summary;
    private String coverImage;
    private Boolean needRegistration;
    private CreatorVO creator;
    private LocalDateTime createdAt;
    private List<ApprovalRecordVO> approvalRecords;

    @Data
    public static class CreatorVO {
        private Long id;
        private String realName;
    }

    @Data
    public static class ApprovalRecordVO {
        private String approverName;
        private Integer approvalLevel;
        private Integer result;
        private String comment;
        private LocalDateTime approvedAt;
    }
}
