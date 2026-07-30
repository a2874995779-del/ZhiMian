package com.zhimian.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimiter {
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 固定窗口 + 单次配额:同一个 key 在 windowSeconds 秒内只放行一次。
     *
     * @param key           限流维度的 key(务必带上 userId,否则会变成全局限流)
     * @param windowSeconds 冷却窗口秒数
     * @return true=本次放行;false=还在冷却窗口内,应当拒绝
     */
    public boolean tryAcquire(String key,long windowSeconds){
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key,"1",windowSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }
}
