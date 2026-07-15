package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.dto.UserLoginDTO;
import com.zhimian.model.dto.UserRegisterDTO;
import com.zhimian.model.vo.LoginVO;
import com.zhimian.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody UserRegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout(){
        userService.logout();
        return Result.success();
    }
}
