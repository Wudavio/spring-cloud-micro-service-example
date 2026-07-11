package com.microservices.auth.config;

import com.microservices.auth.entity.User;
import com.microservices.auth.entity.UserRole;
import com.microservices.auth.entity.UserStatus;
import com.microservices.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 啟動時確保至少有一個 ADMIN 帳號，供管理端與驗證使用。
 * 預設：admin / Admin123!
 */
@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        User admin = new User(
                "admin",
                "admin@example.com",
                passwordEncoder.encode("Admin123!"),
                "System Admin"
        );
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        logger.info("已建立預設管理員帳號: username=admin");
    }
}
