package com.club.management.club.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClubApprovalDTO {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String rejectReason;
}
