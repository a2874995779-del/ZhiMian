package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewTurn {
    private Long id;
    private Long sessionId;
    private Long userId;
    private Integer roundNo;
    private String questionText;
    private String answerText;
    private Integer score;
    private Integer result;
    private String evaluation;
    private Integer status;
    private LocalDateTime answeredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
