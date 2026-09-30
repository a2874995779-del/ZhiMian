package com.zhimian.controller;

import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.service.AiModelSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelSettingsControllerTest {
    @Mock
    private AiModelSettingsService settingsService;
    @InjectMocks
    private AiModelSettingsController controller;

    @AfterEach
    void clearUserContext() {
        UserContext.remove();
    }

    @Test
    void rejectsApiKeySubmissionOverPublicHttp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("zhimian.example.com");
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("Origin", "http://zhimian.example.com");

        assertThatThrownBy(() -> controller.save(settings("model", "sk-secret"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("保存 API Key 必须通过 HTTPS 连接");

        verifyNoInteractions(settingsService);
    }

    @Test
    void ignoresSpoofedHttpsHeaderFromPublicClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThatThrownBy(() -> controller.save(settings("model", "sk-secret"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("保存 API Key 必须通过 HTTPS 连接");

        verifyNoInteractions(settingsService);
    }

    @Test
    void acceptsApiKeyWhenReverseProxyMarksHttps() {
        UserContext.set(7L, "user", "token", System.currentTimeMillis() + 60_000);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("zhimian.example.com");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-Proto", "https");
        when(settingsService.save(eq(7L), any(AiModelSettingsUpdateDTO.class)))
                .thenReturn(new AiModelSettingsVO(true, "SiliconFlow 兼容接口", "model", "••••1234"));

        controller.save(settings("model", "sk-secret"), request);

        ArgumentCaptor<AiModelSettingsUpdateDTO> captor =
                ArgumentCaptor.forClass(AiModelSettingsUpdateDTO.class);
        verify(settingsService).save(eq(7L), captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-secret");
    }

    private AiModelSettingsUpdateDTO settings(String model, String apiKey) {
        AiModelSettingsUpdateDTO dto = new AiModelSettingsUpdateDTO();
        dto.setModel(model);
        dto.setApiKey(apiKey);
        return dto;
    }
}
