package com.zhimian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智面 - AI 面试刷题助手平台 启动类
 */
@EnableScheduling
@SpringBootApplication
public class ZhiMianApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiMianApplication.class, args);
    }
}
