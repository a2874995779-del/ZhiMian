package com.zhimian.model.vo;

import com.zhimian.rag.retrieval.KnowledgeSearchResult;

import java.util.List;

public record KnowledgeSearchResponseVO(
        String query,
        int topK,
        double similarityThreshold,
        int hitCount,
        List<KnowledgeSearchHitVO> hits
) {
    public static KnowledgeSearchResponseVO from(
            KnowledgeSearchResult result) {
        List<KnowledgeSearchHitVO> hits = result.hits().stream()
                .map(KnowledgeSearchHitVO::from)
                .toList();
        return new KnowledgeSearchResponseVO(
                result.query(),
                result.topK(),
                result.similarityThreshold(),
                hits.size(),
                hits
        );
    }
}
