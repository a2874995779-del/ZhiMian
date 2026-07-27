package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InterviewReportVO {
    private Integer score;
    private List<String> highlights;
    private List<String> weaknesses;
    private String summary;
    private LocalDateTime createTime;
}
