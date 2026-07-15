package com.zhimian.service;

import com.zhimian.model.dto.UserLoginDTO;
import com.zhimian.model.dto.UserRegisterDTO;
import com.zhimian.model.vo.LoginVO;
import jakarta.validation.Valid;

public interface UserService {
    Long register(@Valid UserRegisterDTO dto);

    LoginVO login(@Valid UserLoginDTO dto);

    void logout();
}
