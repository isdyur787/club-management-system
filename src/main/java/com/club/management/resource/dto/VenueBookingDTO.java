package com.club.management.resource.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class VenueBookingDTO {

    @NotNull(message = "场地ID不能为空")
    private Long venueId;

    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    @NotNull(message = "预约日期不能为空")
    private LocalDate bookingDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;
}
