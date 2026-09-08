package com.zhimian.model.vo;

import lombok.Data;

@Data
public class InterviewSessionVO {
    private Long id;
    private String direction;
    private String mode;
    private String scenarioCode;
    private String title;
    private String openingMessage;
    private Integer targetQuestionCount;
    private Integer answeredQuestionCount;
}
