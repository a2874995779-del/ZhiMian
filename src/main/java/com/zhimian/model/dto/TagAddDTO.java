package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagAddDTO {
    @NotBlank(message = "标签名不能为空")
    @Size(max=32,message = "标签名最大长度为32个字符")
    private String name;
}

