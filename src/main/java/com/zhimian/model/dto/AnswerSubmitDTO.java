package com.zhimian.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerSubmitDTO {
    @NotNull(message = "答题结果不能为空")
    @Min(value = 0,message = "答题结果取值为0或1")
    @Max(value = 1 ,message = "答题结果取值为0或1")
    private Integer result;
}
