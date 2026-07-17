package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryAddDTO {
    @NotBlank(message = "分类名不能为空")
    @Size(max = 64,message = "分类名最大长度不能超过64")
    private String name;
    @NotNull(message = "parentId不能为空，顶级分类请穿0")
    private Long parentId;
}
