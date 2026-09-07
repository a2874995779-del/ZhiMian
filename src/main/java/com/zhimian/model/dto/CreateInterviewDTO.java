package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CreateInterviewDTO {
    @NotBlank(message = "面试方向不能为空")
    private String direction;

    @Min(value = 5, message = "面试题数不能少于5题")
    @Max(value = 12, message = "面试题数不能超过12题")
    private Integer targetQuestionCount = 8;
}
