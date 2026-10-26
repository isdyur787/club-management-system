package com.club.management.notification.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class NotificationQueryDTO {

    @Min(1)
    private Integer pageNum = 1;
    @Min(1)
    private Integer pageSize = 10;
    private String type;
    private Integer isRead;
}
