package com.club.management.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalDTO {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String comment;
}
