package com.microservices.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "產品圖片資訊")
public class ProductImageDTO {

    @Schema(description = "圖片 ID", example = "1")
    private Long id;

    @Schema(description = "產品 ID", example = "10")
    private Long productId;

    @Schema(description = "原始檔名", example = "front.jpg")
    private String originalFileName;

    @Schema(description = "MIME 類型", example = "image/jpeg")
    private String contentType;

    @Schema(description = "檔案大小（bytes）", example = "204800")
    private long sizeBytes;

    @Schema(description = "排序（越小越前）", example = "0")
    private int sortOrder;

    @Schema(description = "公開存取路徑（可經 Gateway 加 /api 前綴）",
            example = "/products/10/images/1/content")
    private String url;

    @Schema(description = "上傳時間")
    private LocalDateTime createdAt;

    public ProductImageDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
