package com.zhimian.model.vo;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewSessionDetailVO {
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
    private List<InterviewMessageVO> messages;
}
