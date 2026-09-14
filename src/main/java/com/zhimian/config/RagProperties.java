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

    private int chunkSizeTokens = 500;
    private int chunkOverlapTokens = 80;
    private int maxChunksPerDocument = 1000;

    private String embeddingModel = "BAAI/bge-m3";
    private int vectorBatchSize = 16;
    private int ingestionCorePoolSize = 1;
    private int ingestionMaxPoolSize = 2;
    private int ingestionQueueCapacity = 20;
}
