package com.club.management.club.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ClubQueryDTO {

    @Min(1)
    private Integer pageNum = 1;
    @Min(1)
    private Integer pageSize = 10;
    private String clubName;
    private String category;
    private Integer approvalStatus;
}
