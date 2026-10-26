package com.club.management.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("res_venue")
public class Venue extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String venueName;

    private String location;

    private Integer capacity;

    private String facilities;

    private Integer status;
}
