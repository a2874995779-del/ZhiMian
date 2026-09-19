package com.zhimian.model.vo;

import com.zhimian.rag.retrieval.KnowledgeSearchHit;

public record KnowledgeSearchHitVO(
        Long chunkId,
        Long documentId,
        String documentTitle,
        Integer chunkIndex,
        String headingPath,
        String content,
        Double score
) {
    public static KnowledgeSearchHitVO from(KnowledgeSearchHit hit) {
        return new KnowledgeSearchHitVO(
                hit.chunkId(),
                hit.documentId(),
                hit.documentTitle(),
                hit.chunkIndex(),
                hit.headingPath(),
                hit.content(),
                hit.score()
        );
    }
}
