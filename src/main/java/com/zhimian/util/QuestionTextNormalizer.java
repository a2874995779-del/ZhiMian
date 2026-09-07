package com.zhimian.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class QuestionTextNormalizer {
    public String normalize(String source) {
        if (!StringUtils.hasText(source)) {
            return "";
        }

        return Normalizer.normalize(source, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "")
                .replaceFirst("^(请问|请介绍一下|请说明|请解释一下|请解释|谈谈你对|你了解)", "")
                .strip();
    }

    public String fingerprint(String normalizedText) {
        if (!StringUtils.hasText(normalizedText)) {
            throw new IllegalArgumentException("标准化题目不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JDK不支持SHA-256", e);
        }
    }
}
