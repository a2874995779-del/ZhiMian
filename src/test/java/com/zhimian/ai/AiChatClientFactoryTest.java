package com.zhimian.ai;

import com.zhimian.config.JwtProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.UserAiModelConfig;
import com.zhimian.mapper.UserMapper;
import com.zhimian.security.ApiKeyCipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatClientFactoryTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AiChatClientFactory factory = new AiChatClientFactory(
            userMapper,
            cipher(),
            "https://api.siliconflow.cn"
    );

    @Test
    void everyUserNeedsPersonalModelAndKey() {
        assertThatThrownBy(() -> factory.forUser(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先在模型设置中填写模型名称和 API Key");
        assertThatThrownBy(() -> factory.forUser(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先在模型设置中填写模型名称和 API Key");
        verify(userMapper).selectAiModelConfigById(1L);
        verify(userMapper).selectAiModelConfigById(2L);
    }

    @Test
    void personalConfigBuildsClientForThatUser() {
        when(userMapper.selectAiModelConfigById(2L))
                .thenReturn(new UserAiModelConfig("personal-model", cipher().encrypt("sk-personal")));

        assertThat(factory.forUser(2L)).isNotNull();
        verify(userMapper).selectAiModelConfigById(2L);
    }

    private static ApiKeyCipher cipher() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        return new ApiKeyCipher(properties);
    }
}
