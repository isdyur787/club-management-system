package com.club.management.activity.dto;

import com.club.management.common.enums.ActivityType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ActivityCreateDTO {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题不能超过100字")
    private String title;

    @NotBlank(message = "活动描述不能为空")
    @Size(max = 2000, message = "活动描述不能超过2000字")
    private String description;

    @NotNull(message = "活动类型不能为空")
    private ActivityType type;

    @NotBlank(message = "活动地点不能为空")
    @Size(max = 200, message = "活动地点不能超过200字")
    private String location;

    @NotNull(message = "开始时间不能为空")
    @Future(message = "开始时间必须是将来的时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private LocalDateTime registrationDeadline;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限至少为1")
    @Max(value = 10000, message = "人数上限不能超过10000")
    private Integer maxParticipants;

    @NotNull(message = "预算不能为空")
    @DecimalMin(value = "0", message = "预算不能为负数")
    @DecimalMax(value = "100000", message = "预算不能超过100000元")
    private BigDecimal budget;

    private Boolean needRegistration = true;

    private Boolean needApproval = true;

    private String coverImage;
}
