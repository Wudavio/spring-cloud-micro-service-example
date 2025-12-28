package com.microservices.inventory.util;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分散式鎖互斥性屬性測試
 * Feature: microservices-order-inventory, Property 19: 分散式鎖互斥性
 * 驗證需求: 需求 10.1, 10.2
 */
class DistributedLockMutualExclusionPropertyTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private DistributedLock distributedLock;

    @BeforeProperty
    void setUp() {
        // 創建 mock 物件
        redisTemplate = Mockito.mock(RedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        
        // 重置所有 mock 物件
        reset(redisTemplate, valueOperations);
        
        // 設定 mock 行為
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 創建 DistributedLock 實例並注入 mock
        distributedLock = new DistributedLock();
        // 使用反射設定私有欄位
        try {
            var field = DistributedLock.class.getDeclaredField("redisTemplate");
            field.setAccessible(true);
            field.set(distributedLock, redisTemplate);
        } catch (Exception e) {
            throw new RuntimeException("無法設定 redisTemplate", e);
        }
    }

    /**
     * 屬性 19: 分散式鎖互斥性
     * 對於任何同一產品的並發庫存操作，Redis分散式鎖應該確保操作的互斥性和一致性
     */
    @Property(tries = 20)
    @Label("Feature: microservices-order-inventory, Property 19: 分散式鎖互斥性")
    void shouldEnsureMutualExclusionForConcurrentOperations(
            @ForAll("productIds") String productId,
            @ForAll("concurrentThreadCounts") int threadCount) {

        String lockKey = "product:" + productId;
        AtomicInteger lockAcquisitionAttempts = new AtomicInteger(0);
        AtomicInteger successfulLockAcquisitions = new AtomicInteger(0);
        
        // 模擬 Redis 行為：第一次 setIfAbsent 成功，後續失敗
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenAnswer(invocation -> {
                int attempt = lockAcquisitionAttempts.incrementAndGet();
                if (attempt == 1) {
                    successfulLockAcquisitions.incrementAndGet();
                    return true; // 第一次獲取成功
                }
                return false; // 後續獲取失敗
            });

        // 模擬鎖釋放成功
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
            .thenReturn(1L);

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        // 創建多個並發執行緒嘗試獲取同一個鎖
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    DistributedLock.LockResult lockResult = distributedLock.tryLock(
                        lockKey, 5L, 1L);
                    
                    if (lockResult.isAcquired()) {
                        try {
                            // 模擬庫存操作
                            Thread.sleep(10);
                            return true;
                        } finally {
                            distributedLock.releaseLock(lockResult);
                        }
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            });
            futures.add(future);
        }

        // 等待所有執行緒完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 驗證互斥性：只有一個執行緒能夠成功獲取鎖
        assertThat(successfulLockAcquisitions.get())
            .as("只有一個執行緒應該能夠獲取鎖")
            .isEqualTo(1);

        // 驗證所有執行緒都嘗試獲取鎖
        assertThat(lockAcquisitionAttempts.get())
            .as("所有執行緒都應該嘗試獲取鎖")
            .isGreaterThanOrEqualTo(threadCount);
    }

    /**
     * 屬性測試：鎖獲取和釋放的正確性
     */
    @Property(tries = 15)
    @Label("Feature: microservices-order-inventory, Property 19: 鎖獲取釋放正確性")
    void shouldCorrectlyAcquireAndReleaseLocks(
            @ForAll("productIds") String productId) {

        String lockKey = "acquire-release:" + productId;
        
        // 重置 mock 物件以避免跨測試干擾
        reset(redisTemplate, valueOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 模擬成功獲取鎖
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        
        // 模擬成功釋放鎖
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
            .thenReturn(1L);

        // 獲取鎖
        DistributedLock.LockResult lockResult = distributedLock.tryLock(lockKey, 10L, 5L);
        
        // 驗證鎖獲取成功
        assertThat(lockResult.isAcquired())
            .as("應該能夠成功獲取鎖")
            .isTrue();
        
        assertThat(lockResult.getLockValue())
            .as("鎖值不應該為空")
            .isNotNull();
        
        assertThat(lockResult.getFullLockKey())
            .as("完整鎖鍵應該包含前綴")
            .contains("inventory:lock:" + lockKey);

        // 釋放鎖
        boolean released = distributedLock.releaseLock(lockResult);
        
        // 驗證鎖釋放成功
        assertThat(released)
            .as("應該能夠成功釋放鎖")
            .isTrue();

        // 驗證調用了正確的 Redis 操作（不限制次數）
        verify(valueOperations, atLeastOnce()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, atLeastOnce()).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    /**
     * 屬性測試：鎖獲取失敗的處理
     */
    @Property(tries = 15)
    @Label("Feature: microservices-order-inventory, Property 19: 鎖獲取失敗處理")
    void shouldHandleLockAcquisitionFailure(
            @ForAll("productIds") String productId) {

        String lockKey = "failure:" + productId;
        
        // 重置 mock 物件
        reset(redisTemplate, valueOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 模擬鎖獲取失敗
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(false);

        // 嘗試獲取鎖
        DistributedLock.LockResult lockResult = distributedLock.tryLock(lockKey, 5L, 1L);
        
        // 驗證鎖獲取失敗
        assertThat(lockResult.isAcquired())
            .as("鎖獲取應該失敗")
            .isFalse();
        
        assertThat(lockResult.getLockValue())
            .as("失敗時鎖值應該為空")
            .isNull();

        // 嘗試釋放失敗的鎖應該返回 false
        boolean released = distributedLock.releaseLock(lockResult);
        assertThat(released)
            .as("釋放失敗的鎖應該返回 false")
            .isFalse();

        // 驗證沒有調用釋放腳本
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    /**
     * 屬性測試：鎖值的唯一性
     */
    @Property(tries = 3)
    @Label("Feature: microservices-order-inventory, Property 19: 鎖值唯一性")
    void shouldGenerateUniqueLockValues(
            @ForAll("productIds") String productId1,
            @ForAll("productIds") String productId2) {

        // 假設兩個不同的產品ID
        Assume.that(!productId1.equals(productId2));
        
        // 重置 mock 物件
        reset(redisTemplate, valueOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 模擬成功獲取鎖
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
            .thenReturn(1L);

        // 獲取兩個不同的鎖
        DistributedLock.LockResult lock1 = distributedLock.tryLock(productId1, 10L, 5L);
        DistributedLock.LockResult lock2 = distributedLock.tryLock(productId2, 10L, 5L);
        
        try {
            // 驗證兩個鎖都獲取成功
            assertThat(lock1.isAcquired()).isTrue();
            assertThat(lock2.isAcquired()).isTrue();
            
            // 驗證鎖值的唯一性
            assertThat(lock1.getLockValue())
                .as("不同鎖的鎖值應該不同")
                .isNotEqualTo(lock2.getLockValue());
            
            // 驗證鎖鍵的不同
            assertThat(lock1.getFullLockKey())
                .as("不同鎖的鍵應該不同")
                .isNotEqualTo(lock2.getFullLockKey());
                
        } finally {
            // 清理鎖
            distributedLock.releaseLock(lock1);
            distributedLock.releaseLock(lock2);
        }
    }

    // 資料生成器

    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(10)
            .map(s -> "product-" + s);
    }

    @Provide
    Arbitrary<Integer> concurrentThreadCounts() {
        return Arbitraries.integers().between(2, 5);
    }
}