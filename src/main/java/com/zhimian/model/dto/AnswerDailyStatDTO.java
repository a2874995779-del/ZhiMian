package com.zhimian.model.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AnswerDailyStatDTO {
    private LocalDate answerDate;
    private Integer totalCount;
    private Integer correctCount;
}
