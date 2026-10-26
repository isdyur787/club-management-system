package com.club.management.ai.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChatRequestDTO {

    @NotNull(message = "社团ID不能为空")
    private Long clubId;

    private String sessionId;

    @NotBlank(message = "消息不能为空")
    @Size(min = 1, max = 2000, message = "消息长度1-2000字符")
    private String message;
}
