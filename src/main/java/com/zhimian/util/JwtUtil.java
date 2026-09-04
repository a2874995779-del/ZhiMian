package com.zhimian.util;

import com.zhimian.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    public static final String BLACKLIST_KEY_PREFIX = "zhimian:jwt:blacklist:";
    private final JwtProperties jwtProperties;
    /**
     * 签发 token
     * @param userId 用户 id,存入 subject(sub)
     * @param role   角色,存入自定义 claim,供拦截器做权限判断
     *               生成一个带有用户 ID、角色、签发时间和过期时间，并使用密钥签名的 JWT Token
     */
    public String generateToken(Long userId,String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        Instant now = Instant.now();
        Instant expireAt = now.plus(jwtProperties.getExpireHours(), ChronoUnit.HOURS);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("role",role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签。token 被篡改、过期、格式非法都会在这里抛出运行时异常
     * (SignatureException / ExpiredJwtException / MalformedJwtException 等)
     */
    public Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
