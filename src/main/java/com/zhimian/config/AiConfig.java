package com.zhimian.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder){
        return builder.build();
    }
    /**
     * Spring AI 默认的 HTTP 超时较短,面试官型的长回答(尤其是流式场景)容易被提前掐断。
     * 提供自己的 RestClient.Builder,Spring AI 装配 OpenAiApi 时会优先用这个,而不是默认的。
     */
    @Bean
    public RestClient.Builder restClientBuilder(){
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
