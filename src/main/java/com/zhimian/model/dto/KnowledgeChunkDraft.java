package com.zhimian.model.dto;

public record KnowledgeChunkDraft(
        int chunkIndex,
        String headingPath,
        String content,
        String contentHash,
        int tokenCount
) {
}
