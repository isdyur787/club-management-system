package com.club.management.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("res_equipment_borrow")
public class EquipmentBorrow extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private Long activityId;

    private Long clubId;

    private Long borrowerId;

    private Integer borrowCount;

    private LocalDateTime borrowTime;

    private LocalDateTime returnTime;

    private Integer status;
}
