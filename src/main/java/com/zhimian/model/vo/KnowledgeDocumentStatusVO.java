package com.zhimian.model.vo;

import com.zhimian.model.entity.KnowledgeDocument;

import java.time.LocalDateTime;

public record KnowledgeDocumentStatusVO(
        Long id,
        String title,
        Integer status,
        Integer chunkCount,
        String embeddingModel,
        Integer vectorDimension,
        String errorMessage,
        LocalDateTime updateTime
) {
    public static KnowledgeDocumentStatusVO from(
            KnowledgeDocument document) {
        return new KnowledgeDocumentStatusVO(
                document.getId(),
                document.getTitle(),
                document.getStatus(),
                document.getChunkCount(),
                document.getEmbeddingModel(),
                document.getVectorDimension(),
                document.getErrorMessage(),
                document.getUpdateTime()
        );
    }
}