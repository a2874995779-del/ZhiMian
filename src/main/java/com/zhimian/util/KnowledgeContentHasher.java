package com.zhimian.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/*
hash精准去重
 */
@Component
public class KnowledgeContentHasher {
    public String normalize(String content){
        if(!StringUtils.hasText(content)){
            return "";
        }
        return content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    public String sha256(String content){
        String normalized = normalize(content);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    normalized.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        }catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("当前JDK不支持SHA-256",e);
        }
    }
}
