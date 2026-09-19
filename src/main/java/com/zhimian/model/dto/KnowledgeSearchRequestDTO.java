package com.zhimian.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeSearchRequestDTO {
    @NotBlank(message = "检索问题不能为空")
    @Size(max = 500, message = "检索问题最多 500 个字符")
    private String query;

    @Min(value = 1, message = "topK 不能小于 1")
    @Max(value = 20, message = "topK 不能大于 20")
    private Integer topK;

    @DecimalMin(value = "0.0", message = "相似度阈值不能小于 0")
    @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
    private Double similarityThreshold;
}
