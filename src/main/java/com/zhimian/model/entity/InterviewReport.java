package com.zhimian.model.entity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InterviewReport {
    private Long id;
    private Long sessionId;
    private Integer score;
    private String content;   // 存 JSON 字符串,MySQL 侧是 JSON 列
    private Integer status;   // 0-生成中 1-成功 2-失败
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
