package com.microservices.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * 重試機制配置
 * 實現需求 10.3, 10.4: 樂觀鎖衝突時自動重試最多3次，達到上限後返回操作失敗
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * 庫存操作重試模板
     * 針對樂觀鎖衝突和資料庫存取異常進行重試
     */
    @Bean
    public RetryTemplate inventoryRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 設定重試策略
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(OptimisticLockingFailureException.class, true);
        retryableExceptions.put(DataAccessException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 設定退避策略：指數退避，初始延遲1秒，倍數2，最大延遲10秒
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    /**
     * 服務間通信重試模板
     * 針對網路異常和服務暫時不可用進行重試
     */
    @Bean
    public RetryTemplate serviceCallRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 設定重試策略
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(java.net.ConnectException.class, true);
        retryableExceptions.put(java.net.SocketTimeoutException.class, true);
        retryableExceptions.put(java.io.IOException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 設定退避策略：固定延遲500毫秒
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMultiplier(1.5);
        backOffPolicy.setMaxInterval(5000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}