package com.zhimian.controller;

import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import com.zhimian.common.UserContext;
import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.service.AiModelSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

@RestController
@RequestMapping("/api/user/ai-model")
@RequiredArgsConstructor
public class AiModelSettingsController {
    private final AiModelSettingsService settingsService;

    @Value("${zhimian.ai.allow-admin-http:false}")
    private boolean allowAdminHttp;

    @GetMapping
    @RequireAdmin
    public Result<AiModelSettingsVO> get() {
        return Result.success(withTransportPolicy(settingsService.get(UserContext.getUserId())));
    }

    @PutMapping
    @RequireAdmin
    public Result<AiModelSettingsVO> save(
            @Valid @RequestBody AiModelSettingsUpdateDTO dto,
            HttpServletRequest request) {
        requireAllowedTransport(request);
        return Result.success(withTransportPolicy(settingsService.save(UserContext.getUserId(), dto)));
    }

    @PostMapping("/test")
    @RequireAdmin
    public Result<Void> test(HttpServletRequest request) {
        requireAllowedTransport(request);
        settingsService.test(UserContext.getUserId());
        return Result.success();
    }

    @DeleteMapping
    @RequireAdmin
    public Result<Void> clear() {
        settingsService.clear(UserContext.getUserId());
        return Result.success();
    }

    private AiModelSettingsVO withTransportPolicy(AiModelSettingsVO settings) {
        return new AiModelSettingsVO(settings.configured(), settings.provider(), settings.model(),
                settings.maskedApiKey(), adminHttpAllowed());
    }

    private boolean adminHttpAllowed() {
        return allowAdminHttp && "admin".equals(UserContext.getRole());
    }

    private void requireAllowedTransport(HttpServletRequest request) {
        if (!isSecureTransport(request) && !adminHttpAllowed()) {
            throw new BusinessException(ErrorCode.NO_AUTH, "保存或测试模型配置必须通过 HTTPS 连接");
        }
    }

    private boolean isSecureTransport(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String remoteAddress = request.getRemoteAddr();
        boolean localPeer = "127.0.0.1".equals(remoteAddress)
                || "::1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress);
        if (!localPeer) {
            return false;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            return "https".equalsIgnoreCase(forwardedProto.strip());
        }
        String origin = request.getHeader("Origin");
        String host = request.getServerName();
        if (origin != null) {
            try {
                host = URI.create(origin).getHost();
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }
}
