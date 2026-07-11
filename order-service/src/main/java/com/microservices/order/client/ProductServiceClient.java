package com.microservices.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 產品服務客戶端
 */
@FeignClient(name = "product-service", path = "/products")
public interface ProductServiceClient {
    
    /**
     * 獲取產品資訊
     */
    @GetMapping("/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);
    
    /**
     * 產品資料傳輸物件
     */
    class ProductDTO {
        private Long id;
        private String name;
        private String description;
        private java.math.BigDecimal price;
        private String category;
        private String status;
        
        // 預設建構子
        public ProductDTO() {}
        
        // Getters and Setters
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public java.math.BigDecimal getPrice() {
            return price;
        }
        
        public void setPrice(java.math.BigDecimal price) {
            this.price = price;
        }
        
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
}