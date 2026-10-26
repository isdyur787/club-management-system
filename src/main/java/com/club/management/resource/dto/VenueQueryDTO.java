package com.club.management.resource.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class VenueQueryDTO {

    @Min(1)
    private Integer pageNum = 1;
    @Min(1)
    private Integer pageSize = 10;
    private Integer status;
    private Integer capacity;
}
