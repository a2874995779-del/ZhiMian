package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.config.JwtProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.UserLoginDTO;
import com.zhimian.model.dto.UserRegisterDTO;
import com.zhimian.model.entity.User;
import com.zhimian.model.vo.LoginVO;
import com.zhimian.model.vo.UserVO;
import com.zhimian.service.UserService;
import com.zhimian.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtProperties  jwtProperties;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Long register(UserRegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        try{
            user.setRole("user");
            userMapper.insert(user);
        }catch (DuplicateKeyException e){
            throw new BusinessException(ErrorCode.CONFLICT,"用户名已存在");
        }
        return user.getId();
    }

    @Override
    public LoginVO login(UserLoginDTO dto) {
       User user = userMapper.selectByUsername(dto.getUsername());
       if(user == null || !passwordEncoder.matches(dto.getPassword(),user.getPassword())){
           throw new BusinessException(ErrorCode.NOT_LOGIN,"用户名或密码错误");
       }
       String token = jwtUtil.generateToken(user.getId(),user.getRole());
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(userVO);
        return loginVO;
    }

    @Override
    public void logout() {
        String jti = UserContext.getJti();
        long expireAt = UserContext.getExpireAt();
        long remainingSeconds = (expireAt - System.currentTimeMillis()) / 1000;
        // 理论上走到这里 remainingSeconds 必然 > 0(拦截器已经验证过 token 没过期才会放行到这)
        // 但如果 token 恰好在这几毫秒内跨越过期边界,SETEX 一个 <= 0 的 TTL 会直接抛异常,加这个判断兜底
        if(remainingSeconds > 0){
            redisTemplate.opsForValue().set(
                    JwtUtil.BLACKLIST_KEY_PREFIX + jti,
                    "1",
                    remainingSeconds,
                    TimeUnit.SECONDS
            );
        }
    }
}
