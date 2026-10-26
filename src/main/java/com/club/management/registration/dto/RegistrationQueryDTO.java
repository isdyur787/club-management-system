package com.club.management.registration.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RegistrationQueryDTO {

    @Min(1)
    private Integer pageNum = 1;
    @Min(1)
    private Integer pageSize = 10;
    private Integer status;
}
