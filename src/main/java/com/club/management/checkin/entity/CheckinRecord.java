package com.club.management.checkin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_checkin_record")
public class CheckinRecord extends BaseEntity {

    public static final int ADOPTED = 1;
    public static final int NOT_ADOPTED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long activityId;

    private Long clubId;

    private Long userId;

    private Integer rating;

    private String reflection;

    private String problem;

    private String suggestion;

    private String photoUrls;

    private String imageDescriptions;

    private String fileUrls;

    private Integer isAdopted;

    private Integer status;
}
