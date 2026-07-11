package com.microservices.product.service.impl;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.UpdateProductRequest;
import com.microservices.product.entity.Product;
import com.microservices.product.entity.ProductStatus;
import com.microservices.product.event.*;
import com.microservices.product.exception.ProductNotFoundException;
import com.microservices.product.exception.DuplicateProductNameException;
import com.microservices.product.mapper.ProductMapper;
import com.microservices.product.repository.ProductRepository;
import com.microservices.product.service.ProductEventPublisher;
import com.microservices.product.service.ProductImageService;
import com.microservices.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Optional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductEventPublisher eventPublisher;
    private final ProductImageService productImageService;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository, 
                             ProductMapper productMapper,
                             ProductEventPublisher eventPublisher,
                             ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
        this.productImageService = productImageService;
    }

    @Override
    public ProductDTO createProduct(CreateProductRequest request) {
        // Check for duplicate product name
        if (productRepository.existsByName(request.getName())) {
            throw new DuplicateProductNameException("Product with name '" + request.getName() + "' already exists");
        }

        Product product = productMapper.toEntity(request);
        product.setStatus(ProductStatus.ACTIVE);
        
        Product savedProduct = productRepository.save(product);
        ProductDTO result = withImages(productMapper.toDTO(savedProduct));
        
        // Publish product created event
        ProductCreatedEvent event = new ProductCreatedEvent(
                savedProduct.getId(),
                savedProduct.getName(),
                savedProduct.getPrice(),
                savedProduct.getCategory(),
                savedProduct.getStatus()
        );
        eventPublisher.publishEvent(event);
        
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDTO> getProductById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDTO> getActiveProductById(Long id) {
        return productRepository.findByIdAndStatus(id, ProductStatus.ACTIVE)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDTO> getActiveProducts(Pageable pageable) {
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDTO> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDTO> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchByKeywordAndStatus(keyword, ProductStatus.ACTIVE, pageable)
                .map(productMapper::toDTO)
                .map(this::withImages);
    }

    @Override
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        // Check for duplicate name (excluding current product)
        if (productRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DuplicateProductNameException("Product with name '" + request.getName() + "' already exists");
        }

        // Store previous values for event
        ProductStatus previousStatus = product.getStatus();
        java.math.BigDecimal previousPrice = product.getPrice();

        productMapper.updateEntityFromRequest(request, product);
        Product updatedProduct = productRepository.save(product);
        ProductDTO result = withImages(productMapper.toDTO(updatedProduct));
        
        // Publish product updated event
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                updatedProduct.getId(),
                updatedProduct.getName(),
                updatedProduct.getPrice(),
                updatedProduct.getCategory(),
                updatedProduct.getStatus(),
                previousStatus,
                previousPrice
        );
        eventPublisher.publishEvent(event);
        
        return result;
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        
        // TODO: Check for related inventory and orders before deletion
        // This will be implemented when inventory and order services are integrated

        productImageService.deleteAllForProduct(id);
        productRepository.delete(product);
        
        // Publish product deleted event
        ProductDeletedEvent event = new ProductDeletedEvent(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory(),
                product.getStatus()
        );
        eventPublisher.publishEvent(event);
    }

    private ProductDTO withImages(ProductDTO dto) {
        if (dto != null && dto.getId() != null) {
            dto.setImages(productImageService.listImages(dto.getId()));
        }
        return dto;
    }

    @Override
    public void changeProductStatus(Long id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        
        ProductStatus previousStatus = product.getStatus();
        product.setStatus(status);
        LocalDateTime baseline = product.getUpdatedAt();
        if (product.getCreatedAt() != null && (baseline == null || product.getCreatedAt().isAfter(baseline))) {
            baseline = product.getCreatedAt();
        }
        LocalDateTime now = LocalDateTime.now();
        product.setUpdatedAt(baseline == null || now.isAfter(baseline) ? now : baseline.plusNanos(1_000));
        Product updatedProduct = productRepository.save(product);
        
        // Publish product status changed event
        ProductStatusChangedEvent event = new ProductStatusChangedEvent(
                updatedProduct.getId(),
                updatedProduct.getName(),
                updatedProduct.getPrice(),
                updatedProduct.getCategory(),
                updatedProduct.getStatus(),
                previousStatus
        );
        eventPublisher.publishEvent(event);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return productRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProductActive(Long id) {
        return productRepository.findByIdAndStatus(id, ProductStatus.ACTIVE).isPresent();
    }
}
