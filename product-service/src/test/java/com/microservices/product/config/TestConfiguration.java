package com.microservices.product.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.microservices.product")
@EntityScan(basePackages = "com.microservices.product.entity")
@EnableJpaRepositories(basePackages = "com.microservices.product.repository")
public class TestConfiguration {
    public static void main(String[] args) {
        SpringApplication.run(TestConfiguration.class, args);
    }
}