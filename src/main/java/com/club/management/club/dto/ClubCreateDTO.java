package com.club.management.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClubCreateDTO {

    @NotBlank(message = "社团名称不能为空")
    @Size(max = 100, message = "社团名称不能超过100字")
    private String clubName;

    @NotBlank(message = "社团类别不能为空")
    private String category;

    @Size(max = 2000, message = "社团描述不能超过2000字")
    private String description;

    private String charter;
}
