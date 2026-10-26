package com.club.management.activity.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ActivityQueryDTO {

    @Min(1)
    private Integer pageNum = 1;
    @Min(1)
    private Integer pageSize = 10;
    private Long clubId;
    private String status;
    private String type;
    private String keyword;
    private String startTimeFrom;
    private String startTimeTo;
}
