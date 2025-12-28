package com.microservices.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * 資料庫配置類別
 * 配置 JPA 和資料庫連接相關設定
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {

    /**
     * 配置主資料源
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }
}