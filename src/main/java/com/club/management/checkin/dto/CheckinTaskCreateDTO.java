package com.club.management.checkin.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CheckinTaskCreateDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotBlank(message = "任务标题不能为空")
    @Size(min = 1, max = 100, message = "标题长度1-100字符")
    private String title;

    private String description;

    private Boolean requireReflection = true;

    private Boolean requirePhoto = false;

    private Boolean requireSuggestion = false;

    @NotNull(message = "截止时间不能为空")
    @Min(value = 1, message = "截止时间至少1小时")
    @Max(value = 168, message = "截止时间最多7天")
    private Integer deadlineHours;
}
