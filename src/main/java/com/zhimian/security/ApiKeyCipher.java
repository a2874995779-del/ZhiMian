package com.zhimian.security;

import com.zhimian.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyCipher {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION = "HmacSHA256";
    private static final byte[] PURPOSE = "zhimian:user-ai-api-key:v1".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCipher(JwtProperties jwtProperties) {
        try {
            Mac mac = Mac.getInstance(KEY_DERIVATION);
            mac.init(new SecretKeySpec(
                    jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8),
                    KEY_DERIVATION
            ));
            this.encryptionKey = new SecretKeySpec(mac.doFinal(PURPOSE), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化用户 API Key 加密器", exception);
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("用户 API Key 加密失败", exception);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("加密后的 API Key 格式无效");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("用户 API Key 解密失败，请检查服务端密钥是否保持不变", exception);
        }
    }
}
