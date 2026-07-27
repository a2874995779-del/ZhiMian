package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewSessionListVO {
    private Long id;
    private String direction;
    private String title;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
}
