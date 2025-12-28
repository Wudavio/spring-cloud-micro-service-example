package com.microservices.inventory.service;

import com.microservices.inventory.repository.InventoryRepository;
import com.microservices.inventory.repository.InventoryReservationRepository;
import com.microservices.inventory.service.impl.InventoryServiceImpl;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.when;

/**
 * 庫存服務測試基類
 * 提供通用的測試設定和工具方法
 */
public abstract class BaseInventoryServiceTest {

    protected InventoryRepository inventoryRepository;
    protected InventoryReservationRepository reservationRepository;
    protected InventoryLockManager lockManager;
    protected RestTemplate restTemplate;
    protected Environment environment;
    protected InventoryService inventoryService;

    /**
     * 初始化測試環境
     */
    protected void initializeTestEnvironment() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        reservationRepository = Mockito.mock(InventoryReservationRepository.class);
        lockManager = Mockito.mock(InventoryLockManager.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        environment = Mockito.mock(Environment.class);
        
        // 設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        
        inventoryService = new InventoryServiceImpl(
            inventoryRepository, 
            reservationRepository, 
            lockManager, 
            restTemplate, 
            environment
        );
    }

    /**
     * 重置所有 mock 物件並設定測試環境
     */
    protected void resetMocksAndSetupTestEnvironment() {
        Mockito.reset(inventoryRepository, reservationRepository, lockManager, restTemplate, environment);
        
        // 重新設定測試環境
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
    }
}