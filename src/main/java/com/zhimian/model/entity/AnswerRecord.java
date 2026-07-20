package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnswerRecord {
    private Long id;
    private Long userId;
    private Long questionId;
    private Integer result;
    private LocalDateTime createTime;
}
