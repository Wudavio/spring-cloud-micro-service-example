package com.microservices.auth.repository;

import com.microservices.auth.entity.User;
import com.microservices.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用戶資料存取介面
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 根據用戶名查找用戶
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 根據郵箱查找用戶
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 根據用戶名或郵箱查找用戶
     */
    @Query("SELECT u FROM User u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<User> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);
    
    /**
     * 檢查用戶名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 檢查郵箱是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 根據狀態查找用戶
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    
    /**
     * 根據用戶名模糊查詢
     */
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    
    /**
     * 根據全名模糊查詢
     */
    Page<User> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
}