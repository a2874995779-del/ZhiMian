package com.zhimian.model.vo;

import lombok.Data;

@Data
public class InterviewReportStatusVO {
    private Integer status;
    private InterviewReportVO report;
    private String message;

}
