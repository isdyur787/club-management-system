package com.club.management.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("res_venue_booking")
public class VenueBooking extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long venueId;

    private Long activityId;

    private Long clubId;

    private LocalDate bookingDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer status;
}
