package com.club.management.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BorrowDTO {

    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;

    private Long activityId;

    @NotNull(message = "借用数量不能为空")
    @Min(value = 1, message = "借用数量至少为1")
    private Integer borrowCount;
}
