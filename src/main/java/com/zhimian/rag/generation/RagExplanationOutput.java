package com.zhimian.rag.generation;

import java.util.List;

public record RagExplanationOutput(
        String summary,
        List<String> keyPoints,
        List<String> commonMistakes,
        String reviewAdvice
) {
    public RagExplanationOutput {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        commonMistakes = commonMistakes == null
                ? List.of()
                : List.copyOf(commonMistakes);
    }
}
