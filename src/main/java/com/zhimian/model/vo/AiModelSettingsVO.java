package com.zhimian.model.vo;

public record AiModelSettingsVO(boolean configured, String provider, String model, String maskedApiKey) {
}
