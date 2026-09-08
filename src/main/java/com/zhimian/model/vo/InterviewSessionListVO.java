package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSessionListVO {
    private Long id;
    private String direction;
    private String mode;
    private String scenarioCode;
    private String title;
    private Integer status;
    private Integer targetQuestionCount;
    private Integer answeredQuestionCount;
    private String finishReason;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
