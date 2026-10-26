package com.club.management.club.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("club_info")
public class ClubInfo extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String clubName;

    private String category;

    private String description;

    private String charter;

    private String coverImage;

    private Long presidentId;

    private Integer approvalStatus;

    private String rejectReason;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    private Integer memberCount;
}
