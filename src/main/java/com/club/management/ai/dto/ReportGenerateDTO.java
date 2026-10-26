package com.club.management.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportGenerateDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
