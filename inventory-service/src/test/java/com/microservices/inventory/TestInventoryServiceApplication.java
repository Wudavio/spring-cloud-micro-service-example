package com.microservices.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * 測試專用的應用程式類
 * 用於在測試環境中提供特殊配置
 */
@SpringBootApplication
@Profile("test")
public class TestInventoryServiceApplication {

    @MockBean
    private RestTemplate restTemplate;

    public static void main(String[] args) {
        SpringApplication.run(TestInventoryServiceApplication.class, args);
    }
}