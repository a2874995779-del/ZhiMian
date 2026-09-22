package com.zhimian.rag.interview;

import com.zhimian.rag.retrieval.InterviewRagEvidence;

public interface InterviewEvaluationGenerator {
    GroundedEvaluationOutput generate(
            String question,
            String answer,
            InterviewRagEvidence evidence
    );
}
