package com.club.management.signout.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignOutDTO {

    @NotNull(message = "活动ID不能为空")
    private Long activityId;
}
