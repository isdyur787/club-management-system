package com.club.management.club.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinClubDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
