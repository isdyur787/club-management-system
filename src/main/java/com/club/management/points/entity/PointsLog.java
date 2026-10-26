package com.club.management.points.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member_points_log")
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;

    private Long userId;

    private Integer points;

    private String reason;

    private String description;

    private Long relatedActivityId;

    private Long relatedCheckinId;

    private LocalDateTime createdAt;
}
