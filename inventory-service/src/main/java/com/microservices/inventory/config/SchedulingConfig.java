package com.microservices.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定時任務配置類
 * 啟用 Spring 的定時任務功能
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 啟用定時任務功能
    // Spring 會自動掃描帶有 @Scheduled 註解的方法並執行
}