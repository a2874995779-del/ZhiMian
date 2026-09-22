package com.zhimian.rag.interview;

import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.rag.retrieval.InterviewRagEvidence;

public record InterviewEvaluationResult(
        InterviewAnswerEvaluation evaluation,
        InterviewRagEvidence evidence,
        GroundedEvaluationOutput detail
) {
}
