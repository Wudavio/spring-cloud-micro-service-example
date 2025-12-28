package com.microservices.auth.service;

import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.dto.UserDTO;
import com.microservices.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 用戶服務介面
 */
public interface UserService {
    
    /**
     * 用戶註冊
     */
    UserDTO register(RegisterRequest request);
    
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
    Optional<User> findByUsernameOrEmail(String usernameOrEmail);
    
    /**
     * 根據用戶ID查找用戶
     */
    Optional<UserDTO> findById(Long userId);
    
    /**
     * 檢查用戶名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 檢查郵箱是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 獲取所有用戶（分頁）
     */
    Page<UserDTO> getAllUsers(Pageable pageable);
    
    /**
     * 根據用戶名搜索用戶
     */
    Page<UserDTO> searchByUsername(String username, Pageable pageable);
    
    /**
     * 更新用戶資訊
     */
    UserDTO updateUser(Long userId, UserDTO userDTO);
    
    /**
     * 刪除用戶
     */
    void deleteUser(Long userId);
    
    /**
     * 將 User 實體轉換為 UserDTO
     */
    UserDTO convertToDTO(User user);
}