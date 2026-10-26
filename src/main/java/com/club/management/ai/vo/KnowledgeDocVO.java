package com.club.management.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocVO {

    private Long id;

    private Long clubId;

    private String docType;

    private String title;

    private String sourceType;

    private Integer chunkCount;

    private LocalDateTime createdAt;
}
