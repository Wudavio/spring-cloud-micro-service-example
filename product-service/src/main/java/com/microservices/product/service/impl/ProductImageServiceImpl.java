package com.microservices.product.service.impl;

import com.microservices.product.config.ProductImageProperties;
import com.microservices.product.dto.ProductImageDTO;
import com.microservices.product.entity.ProductImage;
import com.microservices.product.exception.ProductNotFoundException;
import com.microservices.product.repository.ProductImageRepository;
import com.microservices.product.repository.ProductRepository;
import com.microservices.product.service.ProductImageService;
import com.microservices.product.service.ProductImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorageService storageService;
    private final ProductImageProperties properties;

    public ProductImageServiceImpl(ProductRepository productRepository,
                                   ProductImageRepository productImageRepository,
                                   ProductImageStorageService storageService,
                                   ProductImageProperties properties) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageDTO> listImages(Long productId) {
        ensureProductExists(productId);
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<ProductImageDTO> uploadImages(Long productId, List<MultipartFile> files) {
        ensureProductExists(productId);

        List<MultipartFile> candidates = files == null ? List.of() : files.stream()
                .filter(Objects::nonNull)
                .filter(f -> !f.isEmpty())
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        long existing = productImageRepository.countByProductId(productId);
        if (existing + candidates.size() > properties.getMaxCount()) {
            throw new IllegalArgumentException(
                    "A product may have at most " + properties.getMaxCount()
                            + " images (current=" + existing + ", uploading=" + candidates.size() + ")");
        }

        int nextOrder = productImageRepository.findMaxSortOrder(productId) + 1;
        List<ProductImageDTO> saved = new ArrayList<>();
        for (MultipartFile file : candidates) {
            validateFile(file);
            try {
                ProductImageStorageService.StoredFile stored = storageService.store(productId, file);
                ProductImage image = new ProductImage();
                image.setProductId(productId);
                image.setStoredFileName(stored.storedFileName());
                image.setOriginalFileName(StringUtils.cleanPath(
                        file.getOriginalFilename() == null ? stored.storedFileName() : file.getOriginalFilename()));
                image.setContentType(file.getContentType());
                image.setSizeBytes(stored.sizeBytes());
                image.setSortOrder(nextOrder++);
                saved.add(toDto(productImageRepository.save(image)));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to store image: " + ex.getMessage(), ex);
            }
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductImageDTO getImage(Long productId, Long imageId) {
        return toDto(requireImage(productId, imageId));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadImageContent(Long productId, Long imageId) {
        ProductImage image = requireImage(productId, imageId);
        try {
            return storageService.loadAsResource(productId, image.getStoredFileName());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load image content", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getContentType(Long productId, Long imageId) {
        ProductImage image = requireImage(productId, imageId);
        return image.getContentType() == null ? "application/octet-stream" : image.getContentType();
    }

    @Override
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = requireImage(productId, imageId);
        productImageRepository.delete(image);
        storageService.deleteQuietly(productId, image.getStoredFileName());
    }

    @Override
    public void deleteAllForProduct(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        productImageRepository.deleteByProductId(productId);
        for (ProductImage image : images) {
            storageService.deleteQuietly(productId, image.getStoredFileName());
        }
        storageService.deleteProductDirectoryQuietly(productId);
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Product not found with id: " + productId);
        }
    }

    private ProductImage requireImage(Long productId, Long imageId) {
        ensureProductExists(productId);
        return productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Image not found with id: " + imageId + " for product: " + productId));
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException(
                    "Image exceeds max size of " + properties.getMaxFileSizeBytes() + " bytes");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean allowed = properties.getAllowedContentTypes().stream()
                .anyMatch(type -> type.equalsIgnoreCase(contentType));
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Unsupported image content type: " + contentType
                            + ". Allowed: " + properties.getAllowedContentTypes());
        }
    }

    private ProductImageDTO toDto(ProductImage image) {
        ProductImageDTO dto = new ProductImageDTO();
        dto.setId(image.getId());
        dto.setProductId(image.getProductId());
        dto.setOriginalFileName(image.getOriginalFileName());
        dto.setContentType(image.getContentType());
        dto.setSizeBytes(image.getSizeBytes());
        dto.setSortOrder(image.getSortOrder());
        dto.setCreatedAt(image.getCreatedAt());
        dto.setUrl("/products/" + image.getProductId() + "/images/" + image.getId() + "/content");
        return dto;
    }
}
