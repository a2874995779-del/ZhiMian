package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.common.UserContext;
import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.AiModelSettingsUpdateDTO;
import com.zhimian.model.vo.AiModelSettingsVO;
import com.zhimian.service.AiModelSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public Result<AiModelSettingsVO> get() {
        return Result.success(settingsService.get(UserContext.getUserId()));
    }

    @PutMapping
    public Result<AiModelSettingsVO> save(
            @Valid @RequestBody AiModelSettingsUpdateDTO dto,
        HttpServletRequest request) {
        if (StringUtils.hasText(dto.getApiKey()) && !isSecureTransport(request)) {
            throw new BusinessException(
                    ErrorCode.NO_AUTH,
                    "保存 API Key 必须通过 HTTPS 连接"
            );
        }
        return Result.success(settingsService.save(UserContext.getUserId(), dto));
    }

    @PostMapping("/test")
    public Result<Void> test() {
        settingsService.test(UserContext.getUserId());
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clear() {
        settingsService.clear(UserContext.getUserId());
        return Result.success();
    }

    private boolean isSecureTransport(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            return "https".equalsIgnoreCase(forwardedProto.strip());
        }
        if (request.isSecure()) {
            return true;
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
