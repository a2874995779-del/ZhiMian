package com.zhimian.model.interview;

public record InterviewPlanItem(
        int roundNo,
        String moduleCode,
        String moduleName,
        String skill,
        String questionType,
        int difficulty,
        String questionText,
        int maxFollowUp
) {
}
