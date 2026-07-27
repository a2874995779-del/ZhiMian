package com.zhimian.model.dto;

import java.util.List;

public record InterviewReportResult (
        Integer score,
        List<String> highlights,
        List<String> weaknesses,
        String summary
) {
}
