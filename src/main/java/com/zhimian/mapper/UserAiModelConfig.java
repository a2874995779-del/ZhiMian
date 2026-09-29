package com.zhimian.mapper;

public class UserAiModelConfig {
    private String model;
    private String encryptedApiKey;

    public UserAiModelConfig() {
    }

    public UserAiModelConfig(String model, String encryptedApiKey) {
        this.model = model;
        this.encryptedApiKey = encryptedApiKey;
    }

    public String model() {
        return model;
    }

    public String encryptedApiKey() {
        return encryptedApiKey;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }
}
