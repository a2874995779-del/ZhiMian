package com.zhimian.ai;

import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.UserAiModelConfig;
import com.zhimian.mapper.UserMapper;
import com.zhimian.security.ApiKeyCipher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiChatClientFactory {
    private final UserMapper userMapper;
    private final ApiKeyCipher apiKeyCipher;
    private final String baseUrl;

    public AiChatClientFactory(
            UserMapper userMapper,
            ApiKeyCipher apiKeyCipher,
            @Value("${spring.ai.openai.base-url}") String baseUrl) {
        this.userMapper = userMapper;
        this.apiKeyCipher = apiKeyCipher;
        this.baseUrl = baseUrl;
    }

    public ChatClient forUser(Long userId) {
        UserAiModelConfig config = userMapper.selectAiModelConfigById(userId);
        if (config == null || !StringUtils.hasText(config.model())
                || !StringUtils.hasText(config.encryptedApiKey())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "请先在模型设置中填写模型名称和 API Key"
            );
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKeyCipher.decrypt(config.encryptedApiKey()))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.model())
                        .temperature(0.7)
                        .build())
                .build();
        return ChatClient.builder(model).build();
    }
}
