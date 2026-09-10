package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zhimian.rag")
public class RagProperties {

    private boolean enabled = true;
    private int topK = 5;
    private double similarityThreshold = 0.70;
    private int embeddingDimensions = 1024;
}
