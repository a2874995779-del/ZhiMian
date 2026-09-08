package com.zhimian.model.interview;

import java.util.List;

public record InterviewPlan(
        String mode,
        String code,
        String title,
        String focus,
        List<InterviewPlanItem> items
) {
}
