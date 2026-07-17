package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Question {
    private Long id;
    private String title;
    private String content;
    private String answer;
    private Integer difficulty;
    private Long categoryId;
    private Integer viewCount;
    private Long createUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
