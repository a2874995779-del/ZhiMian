package com.zhimian.rag.generation;

import com.zhimian.rag.retrieval.KnowledgeSearchHit;

import java.util.List;

public record AssembledKnowledgeContext(
        String promptContext,
        int tokenCount,
        List<KnowledgeSearchHit> includedHits
) {
    public AssembledKnowledgeContext {
        promptContext = promptContext == null ? "" : promptContext;
        includedHits = includedHits == null
                ? List.of()
                : List.copyOf(includedHits);
    }

    public boolean hasKnowledge() {
        return !includedHits.isEmpty();
    }
}
