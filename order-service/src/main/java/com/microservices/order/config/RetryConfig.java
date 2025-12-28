package com.microservices.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import feign.FeignException;
import feign.RetryableException;

import java.util.HashMap;
import java.util.Map;

/**
 * 訂單服務重試機制配置
 * 實現需求 10.3, 10.4: 服務間調用失敗時自動重試最多3次
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * 服務間通信重試模板
     * 針對 Feign 客戶端調用異常進行重試
     */
    @Bean
    public RetryTemplate serviceCallRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 設定重試策略
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RetryableException.class, true);
        retryableExceptions.put(FeignException.ServiceUnavailable.class, true);
        retryableExceptions.put(FeignException.InternalServerError.class, true);
        retryableExceptions.put(FeignException.BadGateway.class, true);
        retryableExceptions.put(FeignException.GatewayTimeout.class, true);
        retryableExceptions.put(java.net.ConnectException.class, true);
        retryableExceptions.put(java.net.SocketTimeoutException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 設定退避策略：指數退避
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMultiplier(1.5);
        backOffPolicy.setMaxInterval(5000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    /**
     * 庫存操作重試模板
     * 針對庫存相關操作進行重試
     */
    @Bean
    public RetryTemplate inventoryOperationRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 設定重試策略
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RuntimeException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 設定退避策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}