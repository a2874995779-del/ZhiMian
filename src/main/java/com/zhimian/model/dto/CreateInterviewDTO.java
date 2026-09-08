package com.zhimian.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInterviewDTO {
    @Size(max = 64, message = "面试方向或场景编码过长")
    private String direction;

    @Size(max = 32, message = "面试模式过长")
    private String mode = "direction";

    @Size(max = 64, message = "综合面试场景编码过长")
    private String scenarioCode;

    @Min(value = 5, message = "面试题数不能少于5题")
    @Max(value = 12, message = "面试题数不能超过12题")
    private Integer targetQuestionCount = 8;
}
