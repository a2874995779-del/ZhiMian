package com.zhimian.rag.interview;

import java.util.List;

public record GroundedEvaluationOutput(
        Integer score,
        String evaluation,
        List<String> coveredPoints,
        List<String> missingPoints,
        Boolean knowledgeSufficient
) {
    public GroundedEvaluationOutput{
        coveredPoints = coveredPoints == null
                ?List.of()
                :List.copyOf(coveredPoints);

        missingPoints = missingPoints == null
                ?List.of()
                :List.copyOf(missingPoints);

        // 保留 null，让模型输出校验能够区分 false 和字段缺失。
    }
}
