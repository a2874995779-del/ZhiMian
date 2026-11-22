package com.zhimian.service.impl;

import com.zhimian.ai.AiChatClientFactory;
import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.UserAiModelConfig;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.security.ApiKeyCipher;
import com.zhimian.service.AiModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiModelSettingsServiceImpl implements AiModelSettingsService {
    private static final String PROVIDER_NAME = "SiliconFlow 兼容接口";

    private final UserMapper userMapper;
    private final ApiKeyCipher apiKeyCipher;
    private final AiChatClientFactory chatClientFactory;

    @Override
    public AiModelSettingsVO get(Long userId) {
        return toView(userMapper.selectAiModelConfigById(userId));
    }

    @Override
    public AiModelSettingsVO save(Long userId, AiModelSettingsUpdateDTO dto) {
        UserAiModelConfig current = userMapper.selectAiModelConfigById(userId);
        String apiKey = StringUtils.hasText(dto.getApiKey()) ? dto.getApiKey().strip() : null;
        String encryptedApiKey = current == null ? null : current.encryptedApiKey();
        if (apiKey != null) {
            encryptedApiKey = apiKeyCipher.encrypt(apiKey);
        }
        if (!StringUtils.hasText(encryptedApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "首次配置必须填写 API Key");
        }

        String model = dto.getModel().strip();
        if (userMapper.updateAiModelConfig(userId, model, encryptedApiKey) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toView(userMapper.selectAiModelConfigById(userId));
    }

    @Override
    public void test(Long userId) {
        try {
            chatClientFactory.forUser(userId)
                    .prompt()
                    .user("Reply with the single word OK.")
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型连接失败，请检查模型名称、API Key 和账户额度");
        }
    }

    @Override
    public void clear(Long userId) {
        userMapper.clearAiModelConfig(userId);
    }

    private AiModelSettingsVO toView(UserAiModelConfig config) {
        if (config == null || !StringUtils.hasText(config.model())
                || !StringUtils.hasText(config.encryptedApiKey())) {
            return new AiModelSettingsVO(false, PROVIDER_NAME, null, null, false);
        }
        String key = apiKeyCipher.decrypt(config.encryptedApiKey());
        String masked = "••••••••" + key.substring(Math.max(0, key.length() - 4));
        return new AiModelSettingsVO(true, PROVIDER_NAME, config.model(), masked, false);
    }
}
