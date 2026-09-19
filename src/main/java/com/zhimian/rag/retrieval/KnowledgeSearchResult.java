package com.zhimian.rag.retrieval;

import java.util.List;

public record KnowledgeSearchResult(
        String query,
        int topK,
        double similarityThreshold,
        List<KnowledgeSearchHit> hits
) {
    public KnowledgeSearchResult {
        hits = List.copyOf(hits);
    }
}
