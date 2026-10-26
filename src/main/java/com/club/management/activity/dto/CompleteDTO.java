package com.club.management.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompleteDTO {

    @NotBlank(message = "活动总结不能为空")
    private String summary;
}
