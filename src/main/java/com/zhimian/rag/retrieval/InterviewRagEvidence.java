package com.zhimian.rag.retrieval;

import com.zhimian.rag.interview.InterviewRagEvidenceStatus;

import java.util.List;

public record InterviewRagEvidence(
        String query,
        String promptContext,
        List<KnowledgeSearchHit> sources,
        InterviewRagEvidenceStatus status
) {
    public InterviewRagEvidence{
        query = query == null ? "" : query;
        promptContext = promptContext == null ? "" : promptContext;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public boolean applied(){
        return status == InterviewRagEvidenceStatus.APPLIED
                && !sources.isEmpty();
    }

    public static InterviewRagEvidence degraded(
            String query,
            InterviewRagEvidenceStatus status
    ){
        return new InterviewRagEvidence(query,"",List.of(),status);
    }
}
