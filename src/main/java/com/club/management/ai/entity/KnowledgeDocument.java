package com.club.management.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.club.management.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clubId;

    private String docType;

    private String title;

    private String sourceType;

    private Long sourceId;

    private String filePath;

    private Integer chunkCount;

    private String chromaIds;

    private Integer status;
}
