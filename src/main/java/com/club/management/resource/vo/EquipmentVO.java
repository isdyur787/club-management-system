package com.club.management.resource.vo;

import lombok.Data;

@Data
public class EquipmentVO {

    private Long id;
    private String equipmentName;
    private String equipmentType;
    private Integer totalCount;
    private Integer availableCount;
    private Integer status;
}
