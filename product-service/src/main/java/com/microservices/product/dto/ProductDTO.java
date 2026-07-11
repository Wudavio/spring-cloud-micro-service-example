package com.microservices.product.dto;

import com.microservices.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "產品資訊")
public class ProductDTO {
    @Schema(description = "產品 ID", example = "1")
    private Long id;
    
    @Schema(description = "產品名稱", example = "iPhone 15 Pro")
    private String name;
    
    @Schema(description = "產品描述", example = "最新款 iPhone，配備 A17 Pro 晶片")
    private String description;
    
    @Schema(description = "產品價格", example = "35900.00")
    private BigDecimal price;
    
    @Schema(description = "產品分類", example = "手機")
    private String category;
    
    @Schema(description = "產品狀態")
    private ProductStatus status;
    
    @Schema(description = "版本號（樂觀鎖）", example = "1")
    private Long version;
    
    @Schema(description = "創建時間")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新時間")
    private LocalDateTime updatedAt;

    @Schema(description = "產品圖片（最多 10 張）")
    private List<ProductImageDTO> images = new ArrayList<>();

    // Default constructor
    public ProductDTO() {}

    // Constructor with all fields
    public ProductDTO(Long id, String name, String description, BigDecimal price, 
                     String category, ProductStatus status, Long version, 
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ProductImageDTO> getImages() {
        return images;
    }

    public void setImages(List<ProductImageDTO> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }
}