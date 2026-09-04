package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WrongQuestionVO {
    private Long id;
    private Long questionId;
    private String title;
    private Integer difficulty;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Integer wrongCount;
    private Integer correctCount;
    private Integer status;
    private LocalDateTime lastWrongTime;
    private LocalDateTime lastReviewTime;
}
