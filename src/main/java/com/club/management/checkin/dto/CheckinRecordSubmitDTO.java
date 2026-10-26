package com.club.management.checkin.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CheckinRecordSubmitDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1分")
    @Max(value = 5, message = "评分最高5分")
    private Integer rating;

    private String reflection;

    private String problem;

    private String suggestion;

    private List<String> photoUrls;

    private List<String> fileUrls;
}
