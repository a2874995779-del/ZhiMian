package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInterviewDTO {
    @NotBlank(message = "面试方向不能为空")
    private String direction;
}
