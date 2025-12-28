package com.microservices.auth.service.impl;

import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.dto.UserDTO;
import com.microservices.auth.entity.User;
import com.microservices.auth.exception.UserAlreadyExistsException;
import com.microservices.auth.exception.UserNotFoundException;
import com.microservices.auth.repository.UserRepository;
import com.microservices.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用戶服務實現
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public UserDTO register(RegisterRequest request) {
        logger.info("用戶註冊: username={}, email={}", request.getUsername(), request.getEmail());
        
        // 檢查用戶名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("用戶名已存在: " + request.getUsername());
        }
        
        // 檢查郵箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("郵箱已存在: " + request.getEmail());
        }
        
        // 檢查密碼確認
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("密碼和確認密碼不匹配");
        }
        
        // 創建新用戶
        User user = new User(
            request.getUsername(),
            request.getEmail(),
            passwordEncoder.encode(request.getPassword()),
            request.getFullName()
        );
        user.setPhoneNumber(request.getPhoneNumber());
        
        user = userRepository.save(user);
        
        logger.info("用戶註冊成功: userId={}, username={}", user.getUserId(), user.getUsername());
        
        return convertToDTO(user);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findById(Long userId) {
        return userRepository.findById(userId)
                .map(this::convertToDTO);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::convertToDTO);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> searchByUsername(String username, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(username, pageable)
                .map(this::convertToDTO);
    }
    
    @Override
    public UserDTO updateUser(Long userId, UserDTO userDTO) {
        logger.info("更新用戶資訊: userId={}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用戶不存在: " + userId));
        
        // 更新用戶資訊
        if (userDTO.getFullName() != null) {
            user.setFullName(userDTO.getFullName());
        }
        if (userDTO.getPhoneNumber() != null) {
            user.setPhoneNumber(userDTO.getPhoneNumber());
        }
        if (userDTO.getStatus() != null) {
            user.setStatus(userDTO.getStatus());
        }
        if (userDTO.getRole() != null) {
            user.setRole(userDTO.getRole());
        }
        
        user = userRepository.save(user);
        
        logger.info("用戶資訊更新成功: userId={}", userId);
        
        return convertToDTO(user);
    }
    
    @Override
    public void deleteUser(Long userId) {
        logger.info("刪除用戶: userId={}", userId);
        
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("用戶不存在: " + userId);
        }
        
        userRepository.deleteById(userId);
        
        logger.info("用戶刪除成功: userId={}", userId);
    }
    
    @Override
    public UserDTO convertToDTO(User user) {
        return new UserDTO(
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPhoneNumber(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}