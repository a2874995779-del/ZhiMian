package com.zhimian.model.vo;

import com.zhimian.model.enums.RagDegradedReason;
import com.zhimian.model.enums.RagExplanationMode;

import java.util.List;

public record WrongQuestionExplanationVO(
        Long questionId,
        String questionTitle,
        String summary,
        List<String> keyPoints,
        List<String> commonMistakes,
        String reviewAdvice,
        RagExplanationMode mode,
        boolean ragApplied,
        boolean aiGenerated,
        List<RagDegradedReason> degradedReasons,
        List<KnowledgeCitationVO> citations
) {
    public WrongQuestionExplanationVO {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        commonMistakes = commonMistakes == null
                ? List.of()
                : List.copyOf(commonMistakes);
        degradedReasons = degradedReasons == null
                ? List.of()
                : List.copyOf(degradedReasons);
        citations = citations == null
                ? List.of()
                : List.copyOf(citations);
    }
}
