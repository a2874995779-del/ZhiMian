package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4,max = 32,message = "用户名长度需在4-32之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;
    @NotBlank(message = "用户名不能为空")
    @Size(min = 8,max = 24,message = "密码长度需在8-24之间")
    private String password;
    @Size(max = 16,message = "昵称最大长度为16个字符")
    private String nickname;
}
