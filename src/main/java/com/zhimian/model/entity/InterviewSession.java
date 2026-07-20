package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSession {
    private Long id;
    private Long userId;
    private String direction;
    private String title;
    private Integer status;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
