package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeChunk {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String headingPath;
    private String content;
    private String contentHash;
    private Integer tokenCount;
    private Integer vectorStatus;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
