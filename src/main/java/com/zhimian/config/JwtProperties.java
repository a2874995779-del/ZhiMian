package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhimian.jwt")
public class JwtProperties {
    /** 签名密钥,长度必须 >= 32 字节,配在 application-local.yml,不进 git */
    private String secret;
    /** token 有效期(小时) */
    private long expireHours;
}
