package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WrongQuestion {
    private Long id;
    private Long userId;
    private Long questionId;
    private Integer wrongCount;
    private Integer correctCount;
    private Integer status;
    private LocalDateTime lastWrongTime;
    private LocalDateTime lastReviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
