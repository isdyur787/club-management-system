package com.club.management.registration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
