package com.zhimian.rag.retrieval;

import lombok.Data;

@Data
public class KnowledgeSearchRow {
    private Long chunkId;
    private Long documentId;
    private String documentTitle;
    private String sourceType;
    private Integer chunkIndex;
    private String headingPath;
    private String content;
    private String contentHash;
    private Integer tokenCount;
}
