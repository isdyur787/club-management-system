package com.club.management.ai.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ExperienceRequestDTO {

    @NotNull(message = "社团ID不能为空")
    private Long clubId;

    @NotBlank(message = "活动类型不能为空")
    private String activityType;

    @NotBlank(message = "活动标题不能为空")
    private String title;

    private String location;

    private Integer maxParticipants;

    private Double budget;
}
