package com.microservices.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.client.ConfigClientAutoConfiguration;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;

@SpringBootApplication(exclude = {
    ConfigClientAutoConfiguration.class,
    EurekaClientAutoConfiguration.class
})
public class TestOrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestOrderServiceApplication.class, args);
    }
}