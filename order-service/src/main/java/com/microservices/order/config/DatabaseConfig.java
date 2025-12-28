package com.microservices.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 資料庫配置
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.microservices.order.repository")
@EnableTransactionManagement
public class DatabaseConfig {
}