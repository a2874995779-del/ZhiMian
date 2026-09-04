package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionFavorite {
    private Long id;
    private Long userId;
    private Long questionId;
    private LocalDateTime createTime;
}
