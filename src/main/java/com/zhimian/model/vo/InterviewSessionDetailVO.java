package com.zhimian.model.vo;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewSessionDetailVO {
    private Long id;
    private String direction;
    private String title;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private List<InterviewMessageVO> messages;
}
