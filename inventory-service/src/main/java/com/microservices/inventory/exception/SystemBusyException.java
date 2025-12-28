package com.microservices.inventory.exception;

/**
 * 系統繁忙異常
 * 當系統負載過高或資源競爭激烈時拋出此異常
 * 實現需求 10.8: 分散式鎖獲取超時時，返回系統繁忙錯誤並建議客戶稍後重試
 */
public class SystemBusyException extends RuntimeException {
    
    private final String resource;
    private final String suggestion;
    
    public SystemBusyException(String resource) {
        this(resource, "系統繁忙，請稍後重試");
    }
    
    public SystemBusyException(String resource, String suggestion) {
        super(String.format("系統繁忙，無法處理 %s 操作。%s", resource, suggestion));
        this.resource = resource;
        this.suggestion = suggestion;
    }
    
    public SystemBusyException(String resource, String suggestion, Throwable cause) {
        super(String.format("系統繁忙，無法處理 %s 操作。%s", resource, suggestion), cause);
        this.resource = resource;
        this.suggestion = suggestion;
    }
    
    public String getResource() {
        return resource;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
}