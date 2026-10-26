package com.club.management.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long clubId;

    private Long userId;

    private String title;

    private Integer messageCount;

    private LocalDateTime lastActiveAt;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
