package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocument {
    private Long id;
    private String title;
    private String originalFilename;
    private String sourceType;
    private String content;
    private String contentHash;
    private Integer status;
    private Integer chunkCount;
    private String embeddingModel;
    private Integer vectorDimension;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
