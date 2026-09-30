package com.zhimian.service.impl;

import com.zhimian.ai.AiChatClientFactory;
import com.zhimian.config.JwtProperties;
import com.zhimian.mapper.UserAiModelConfig;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.security.ApiKeyCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelSettingsServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private AiChatClientFactory chatClientFactory;

    private ApiKeyCipher cipher;
    private AiModelSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        cipher = new ApiKeyCipher(properties);
        service = new AiModelSettingsServiceImpl(userMapper, cipher, chatClientFactory);
    }

    @Test
    void savesEncryptedKeyAndOnlyReturnsMaskedValue() {
        AiModelSettingsUpdateDTO dto = request("deepseek-ai/DeepSeek-V3.2", "sk-private-key-1234");
        when(userMapper.selectAiModelConfigById(7L))
                .thenReturn(null)
                .thenAnswer(invocation -> new UserAiModelConfig(
                        "deepseek-ai/DeepSeek-V3.2", savedCiphertext.get()));
        when(userMapper.updateAiModelConfig(eq(7L), eq("deepseek-ai/DeepSeek-V3.2"), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    savedCiphertext.set(invocation.getArgument(2));
                    return 1;
                });

        AiModelSettingsVO result = service.save(7L, dto);

        assertThat(savedCiphertext.get()).isNotEqualTo("sk-private-key-1234");
        assertThat(cipher.decrypt(savedCiphertext.get())).isEqualTo("sk-private-key-1234");
        assertThat(result.configured()).isTrue();
        assertThat(result.maskedApiKey()).endsWith("1234").doesNotContain("sk-private-key");
    }

    @Test
    void changingModelWithoutKeyKeepsExistingSecret() {
        String encrypted = cipher.encrypt("sk-existing-key-9876");
        when(userMapper.selectAiModelConfigById(7L))
                .thenReturn(new UserAiModelConfig("old-model", encrypted))
                .thenReturn(new UserAiModelConfig("new-model", encrypted));
        when(userMapper.updateAiModelConfig(7L, "new-model", encrypted)).thenReturn(1);

        AiModelSettingsVO result = service.save(7L, request("new-model", " "));

        verify(userMapper).updateAiModelConfig(7L, "new-model", encrypted);
        assertThat(result.model()).isEqualTo("new-model");
        assertThat(result.maskedApiKey()).endsWith("9876");
    }

    @Test
    void unconfiguredUserHasNoSharedFallback() {
        when(userMapper.selectAiModelConfigById(7L)).thenReturn(null);

        AiModelSettingsVO result = service.get(7L);

        assertThat(result.configured()).isFalse();
        assertThat(result.model()).isNull();
        assertThat(result.maskedApiKey()).isNull();
    }

    @Test
    void getReturnsOnlyPersonalConfiguration() {
        when(userMapper.selectAiModelConfigById(7L))
                .thenReturn(new UserAiModelConfig("personal-model", cipher.encrypt("sk-personal-9876")));

        AiModelSettingsVO result = service.get(7L);

        assertThat(result.model()).isEqualTo("personal-model");
        assertThat(result.maskedApiKey()).endsWith("9876");
    }

    private AiModelSettingsUpdateDTO request(String model, String apiKey) {
        AiModelSettingsUpdateDTO dto = new AiModelSettingsUpdateDTO();
        dto.setModel(model);
        dto.setApiKey(apiKey);
        return dto;
    }

    private final java.util.concurrent.atomic.AtomicReference<String> savedCiphertext =
            new java.util.concurrent.atomic.AtomicReference<>();
}
