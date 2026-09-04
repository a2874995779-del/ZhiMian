package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.config.JwtProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.UserLoginDTO;
import com.zhimian.model.dto.UserRegisterDTO;
import com.zhimian.model.entity.User;
import com.zhimian.model.vo.LoginVO;
import com.zhimian.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    private BCryptPasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("12345678901234567890123456789012");
        jwtProperties.setExpireHours(24);
        jwtUtil = new JwtUtil(jwtProperties);
        userService = new UserServiceImpl(userMapper, passwordEncoder, jwtProperties, jwtUtil, redisTemplate);
    }

    @Test
    void registerEncodesPasswordAndUsesUsernameAsDefaultNickname() {
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return null;
        }).when(userMapper).insert(org.mockito.Mockito.any(User.class));

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("user01");
        dto.setPassword("password123");

        Long userId = userService.register(dto);

        assertThat(userId).isEqualTo(10L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("user01");
        assertThat(savedUser.getNickname()).isEqualTo("user01");
        assertThat(savedUser.getRole()).isEqualTo("user");
        assertThat(savedUser.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();
    }

    @Test
    void registerDuplicateUsernameThrowsConflict() {
        doThrow(new DuplicateKeyException("duplicate")).when(userMapper).insert(org.mockito.Mockito.any(User.class));

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("user01");
        dto.setPassword("password123");

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.CONFLICT.getCode());
    }

    @Test
    void loginReturnsTokenAndUserInfoWhenPasswordMatches() {
        User user = new User();
        user.setId(8L);
        user.setUsername("user01");
        user.setNickname("小智");
        user.setRole("user");
        user.setPassword(passwordEncoder.encode("password123"));
        when(userMapper.selectByUsername("user01")).thenReturn(user);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("user01");
        dto.setPassword("password123");

        LoginVO login = userService.login(dto);

        assertThat(login.getToken()).isNotBlank();
        Claims claims = jwtUtil.parseToken(login.getToken());
        assertThat(claims.getSubject()).isEqualTo("8");
        assertThat(claims.get("role", String.class)).isEqualTo("user");
        assertThat(login.getUser().getId()).isEqualTo(8L);
        assertThat(login.getUser().getUsername()).isEqualTo("user01");
        assertThat(login.getUser().getNickname()).isEqualTo("小智");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User();
        user.setPassword(passwordEncoder.encode("password123"));
        when(userMapper.selectByUsername("user01")).thenReturn(user);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("user01");
        dto.setPassword("wrong-password");

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_LOGIN.getCode());
    }
}
