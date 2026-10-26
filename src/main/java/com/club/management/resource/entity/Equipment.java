package com.club.management.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("res_equipment")
public class Equipment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String equipmentName;

    private String equipmentType;

    private Integer totalCount;

    private Integer availableCount;

    @Version
    private Integer version;

    private Integer status;
}
