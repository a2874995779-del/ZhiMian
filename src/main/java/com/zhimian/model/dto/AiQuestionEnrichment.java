package com.zhimian.model.dto;

import java.util.List;

public record AiQuestionEnrichment (
    String answer,
    Integer difficulty,
    Long categoryId,
    List<Long> tagIds
){}
