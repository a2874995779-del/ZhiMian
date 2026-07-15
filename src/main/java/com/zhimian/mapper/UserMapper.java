package com.zhimian.mapper;

import com.zhimian.model.entity.User;
import jakarta.validation.constraints.NotBlank;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    void insert(User user);

    User selectByUsername(@NotBlank(message = "用户名不能为空") String username);
}
