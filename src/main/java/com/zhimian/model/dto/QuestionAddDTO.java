package com.zhimian.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class QuestionAddDTO {
    @NotBlank(message = "题目标题不能为空")
    @Size(max = 256 ,message = "标题题目最大长度为256个字符")
    private String title;
    private String content;
    @NotBlank(message = "参考答案不能为空")
    private String answer;
    @NotNull(message = "难度不能为空")
    @Min(value = 1,message = "难度值取值为1/2/3")
    @Max(value = 3,message = "难度值取值为1/2/3")
    private Integer difficulty;
    @NotNull(message = "分类不能为空")
    private Long categoryId;
    private List<Long> tagIds;
}
