package com.zhimian.controller;

import com.zhimian.common.UserContext;
import com.zhimian.common.ErrorCode;
import com.zhimian.config.JwtProperties;
import com.zhimian.exception.GlobalExceptionHandler;
import com.zhimian.interceptor.JwtInterceptor;
import com.zhimian.util.JwtUtil;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.service.AiModelSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class AiModelSettingsControllerTest {
    @Mock
    private AiModelSettingsService settingsService;
    @InjectMocks
    private AiModelSettingsController controller;

    @BeforeEach
    void setAdminContext() {
        UserContext.set(7L, "admin", "token", System.currentTimeMillis() + 60_000);
    }

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
                .hasMessage("保存或测试模型配置必须通过 HTTPS 连接");

        verifyNoInteractions(settingsService);
    }

    @Test
    void ignoresSpoofedHttpsHeaderFromPublicClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThatThrownBy(() -> controller.save(settings("model", "sk-secret"), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("保存或测试模型配置必须通过 HTTPS 连接");

        verifyNoInteractions(settingsService);
    }

    @Test
    void acceptsApiKeyWhenReverseProxyMarksHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("zhimian.example.com");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-Proto", "https");
        when(settingsService.save(eq(7L), any(AiModelSettingsUpdateDTO.class)))
                .thenReturn(view());

        controller.save(settings("model", "sk-secret"), request);

        ArgumentCaptor<AiModelSettingsUpdateDTO> captor =
                ArgumentCaptor.forClass(AiModelSettingsUpdateDTO.class);
        verify(settingsService).save(eq(7L), captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-secret");
    }

    @Test
    void adminCanSaveOverHttpOnlyWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);
        when(settingsService.save(eq(7L), any(AiModelSettingsUpdateDTO.class))).thenReturn(view());

        AiModelSettingsVO result = controller.save(settings("model", "sk-secret"), publicHttp()).getData();

        assertThat(result.httpAllowed()).isTrue();
        verify(settingsService).save(eq(7L), any(AiModelSettingsUpdateDTO.class));
    }

    @Test
    void httpExceptionDoesNotApplyToOrdinaryUsers() {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);
        UserContext.set(8L, "user", "token", System.currentTimeMillis() + 60_000);

        assertThatThrownBy(() -> controller.save(settings("model", "sk-secret"), publicHttp()))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(settingsService);
    }

    @Test
    void adminHttpPolicyDefaultsToDisabled() {
        when(settingsService.get(7L)).thenReturn(view());

        assertThat(controller.get().getData().httpAllowed()).isFalse();
    }

    @Test
    void getAdvertisesEnabledPolicyToAdmin() {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);
        when(settingsService.get(7L)).thenReturn(view());

        assertThat(controller.get().getData().httpAllowed()).isTrue();
    }

    @Test
    void modelOnlyUpdateStillRequiresAllowedTransport() {
        assertThatThrownBy(() -> controller.save(settings("other-model", null), publicHttp()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(settingsService);
    }

    @Test
    void publicHttpConnectionTestIsDisabledByDefault() {
        assertThatThrownBy(() -> controller.test(publicHttp())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(settingsService);
    }

    @Test
    void adminCanTestOverHttpWhenEnabled() {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);

        controller.test(publicHttp());

        verify(settingsService).test(7L);
    }

    @Test
    void adminCanSaveOverDirectHttpsWithoutHttpException() {
        MockHttpServletRequest request = publicHttp();
        request.setSecure(true);
        when(settingsService.save(eq(7L), any(AiModelSettingsUpdateDTO.class))).thenReturn(view());

        controller.save(settings("model", "sk-secret"), request);

        verify(settingsService).save(eq(7L), any(AiModelSettingsUpdateDTO.class));
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/user/ai-model", "PUT,/api/user/ai-model",
            "POST,/api/user/ai-model/test", "DELETE,/api/user/ai-model"})
    void realAuthInterceptorRejectsOrdinaryUserOnEveryEndpoint(String method, String path) throws Exception {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);
        JwtUtil jwt = testJwt();
        MockMvc mvc = authenticatedMvc(jwt);

        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .secure(true)
                        .header("Authorization", "Bearer " + jwt.generateToken(8L, "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"model\",\"apiKey\":\"sk-secret\"}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH.getCode()));

        verifyNoInteractions(settingsService);
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void realAuthInterceptorRejectsAnonymousRequest() throws Exception {
        UserContext.remove();
        authenticatedMvc(testJwt()).perform(request(HttpMethod.GET, "/api/user/ai-model"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN.getCode()));
        verifyNoInteractions(settingsService);
    }

    @Test
    void realAuthInterceptorAllowsAdminToSaveOverHttpWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(controller, "allowAdminHttp", true);
        JwtUtil jwt = testJwt();
        when(settingsService.save(eq(7L), any(AiModelSettingsUpdateDTO.class))).thenReturn(view());

        authenticatedMvc(jwt).perform(request(HttpMethod.PUT, "/api/user/ai-model")
                        .with(req -> { req.setRemoteAddr("203.0.113.5"); return req; })
                        .header("Authorization", "Bearer " + jwt.generateToken(7L, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"model\",\"apiKey\":\"sk-secret\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.httpAllowed").value(true));

        verify(settingsService).save(eq(7L), any(AiModelSettingsUpdateDTO.class));
    }

    private JwtUtil testJwt() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        properties.setExpireHours(1);
        return new JwtUtil(properties);
    }

    private MockMvc authenticatedMvc(JwtUtil jwt) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new JwtInterceptor(jwt, mock(StringRedisTemplate.class)))
                .build();
    }

    private MockHttpServletRequest publicHttp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("zhimian.example.com");
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("Origin", "http://zhimian.example.com");
        return request;
    }

    private AiModelSettingsVO view() {
        return new AiModelSettingsVO(true, "SiliconFlow 兼容接口", "model", "••••1234", false);
    }

    private AiModelSettingsUpdateDTO settings(String model, String apiKey) {
        AiModelSettingsUpdateDTO dto = new AiModelSettingsUpdateDTO();
        dto.setModel(model);
        dto.setApiKey(apiKey);
        return dto;
    }
}
