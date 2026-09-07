package com.zhimian.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiQuestionCandidateQueryDTO {
    @Min(value = 1, message = "页码必须从1开始")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 50, message = "每页最多50条")
    private Integer pageSize = 10;

    @Min(value = 0, message = "候选题状态不合法")
    @Max(value = 5, message = "候选题状态不合法")
    private Integer status;

    @Size(max = 32, message = "面试方向最多32个字符")
    private String direction;

    @Size(max = 100, message = "关键词最多100个字符")
    private String keyword;

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
