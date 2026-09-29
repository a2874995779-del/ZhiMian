package com.zhimian.security;

import com.zhimian.config.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCipherTest {

    @Test
    void encryptsWithRandomNonceAndCanDecrypt() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        ApiKeyCipher cipher = new ApiKeyCipher(properties);

        String first = cipher.encrypt("sk-user-private-api-key");
        String second = cipher.encrypt("sk-user-private-api-key");

        assertThat(first).isNotEqualTo("sk-user-private-api-key");
        assertThat(second).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-user-private-api-key");
    }
}
