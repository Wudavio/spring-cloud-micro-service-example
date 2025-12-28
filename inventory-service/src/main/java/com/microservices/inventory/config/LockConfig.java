package com.microservices.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 分散式鎖配置類別
 * 管理鎖相關的配置參數
 */
@Configuration
@ConfigurationProperties(prefix = "inventory.lock")
public class LockConfig {

    /**
     * 預設鎖持有超時時間（秒）
     */
    private long defaultLockTimeoutSeconds = 30L;
    
    /**
     * 預設獲取鎖超時時間（秒）
     */
    private long defaultAcquireTimeoutSeconds = 10L;
    
    /**
     * 鎖重試間隔時間（毫秒）
     */
    private long retryIntervalMillis = 50L;
    
    /**
     * 是否啟用鎖監控
     */
    private boolean monitoringEnabled = true;
    
    /**
     * 鎖監控日誌級別
     */
    private String monitoringLogLevel = "DEBUG";
    
    /**
     * 最大並發鎖數量（用於監控）
     */
    private int maxConcurrentLocks = 1000;

    // Getters and Setters
    
    public long getDefaultLockTimeoutSeconds() {
        return defaultLockTimeoutSeconds;
    }

    public void setDefaultLockTimeoutSeconds(long defaultLockTimeoutSeconds) {
        this.defaultLockTimeoutSeconds = defaultLockTimeoutSeconds;
    }

    public long getDefaultAcquireTimeoutSeconds() {
        return defaultAcquireTimeoutSeconds;
    }

    public void setDefaultAcquireTimeoutSeconds(long defaultAcquireTimeoutSeconds) {
        this.defaultAcquireTimeoutSeconds = defaultAcquireTimeoutSeconds;
    }

    public long getRetryIntervalMillis() {
        return retryIntervalMillis;
    }

    public void setRetryIntervalMillis(long retryIntervalMillis) {
        this.retryIntervalMillis = retryIntervalMillis;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public String getMonitoringLogLevel() {
        return monitoringLogLevel;
    }

    public void setMonitoringLogLevel(String monitoringLogLevel) {
        this.monitoringLogLevel = monitoringLogLevel;
    }

    public int getMaxConcurrentLocks() {
        return maxConcurrentLocks;
    }

    public void setMaxConcurrentLocks(int maxConcurrentLocks) {
        this.maxConcurrentLocks = maxConcurrentLocks;
    }

    @Override
    public String toString() {
        return "LockConfig{" +
                "defaultLockTimeoutSeconds=" + defaultLockTimeoutSeconds +
                ", defaultAcquireTimeoutSeconds=" + defaultAcquireTimeoutSeconds +
                ", retryIntervalMillis=" + retryIntervalMillis +
                ", monitoringEnabled=" + monitoringEnabled +
                ", monitoringLogLevel='" + monitoringLogLevel + '\'' +
                ", maxConcurrentLocks=" + maxConcurrentLocks +
                '}';
    }
}