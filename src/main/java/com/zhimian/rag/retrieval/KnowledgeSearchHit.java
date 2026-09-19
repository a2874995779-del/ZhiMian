package com.zhimian.rag.retrieval;

public record KnowledgeSearchHit(
        Long chunkId,
        Long documentId,
        String documentTitle,
        Integer chunkIndex,
        String headingPath,
        String content,
        Double score
) {
}
