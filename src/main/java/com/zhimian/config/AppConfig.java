package com.zhimian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {
    @Bean
    public BCryptPasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean("reportExecutor")
    @Primary
    public Executor reportExecutor() {
        return new ThreadPoolExecutor(
                2,
                4,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("report-generator-" + thread.getId());
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean("questionCollectExecutor")
    public Executor questionCollectExecutor() {
        return new ThreadPoolExecutor(
                1,
                2,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("question-collector-" + thread.getId());
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean(name = "ragIngestionExecutor", destroyMethod = "shutdown")
    public Executor ragIngestionExecutor(RagProperties properties) {
        return new ThreadPoolExecutor(
                properties.getIngestionCorePoolSize(),
                properties.getIngestionMaxPoolSize(),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(
                        properties.getIngestionQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("rag-ingestion-" + thread.getId());
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
