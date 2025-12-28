package com.microservices.product.service;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.UpdateProductRequest;
import com.microservices.product.entity.Product;
import com.microservices.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductService {
    
    ProductDTO createProduct(CreateProductRequest request);
    
    Optional<ProductDTO> getProductById(Long id);
    
    Optional<ProductDTO> getActiveProductById(Long id);
    
    Page<ProductDTO> getAllProducts(Pageable pageable);
    
    Page<ProductDTO> getActiveProducts(Pageable pageable);
    
    Page<ProductDTO> getProductsByCategory(String category, Pageable pageable);
    
    Page<ProductDTO> searchProducts(String keyword, Pageable pageable);
    
    ProductDTO updateProduct(Long id, UpdateProductRequest request);
    
    void deleteProduct(Long id);
    
    void changeProductStatus(Long id, ProductStatus status);
    
    boolean existsById(Long id);
    
    boolean isProductActive(Long id);
}