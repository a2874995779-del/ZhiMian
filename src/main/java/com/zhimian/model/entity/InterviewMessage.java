package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer tokens;
    private LocalDateTime createTime;
}
