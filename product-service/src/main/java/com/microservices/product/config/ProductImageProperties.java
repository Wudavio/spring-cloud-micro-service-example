package com.microservices.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "product.images")
public class ProductImageProperties {

    /**
     * Relative to the product-service working directory (or absolute path).
     * Default: product-service/uploads/products
     */
    private String uploadDir = "uploads/products";

    private int maxCount = 10;

    private long maxFileSizeBytes = 5L * 1024 * 1024;

    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    ));

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
