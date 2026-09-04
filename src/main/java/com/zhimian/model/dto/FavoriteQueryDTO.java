package com.zhimian.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class FavoriteQueryDTO {
    @Min(value = 1,message = "页码必须从1开始")
    private Integer pageNum=1;
    @Min(value = 1,message = "每页数量必须大于0")
    @Max(value = 50,message = "每页最多50条")
    private Integer pageSize = 10;

    private Long categoryId;
    private Integer difficulty;
    private String keyword;
}
