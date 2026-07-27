package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewMessageVO {
    private String role;
    private String content;
    private LocalDateTime createTime;
}
