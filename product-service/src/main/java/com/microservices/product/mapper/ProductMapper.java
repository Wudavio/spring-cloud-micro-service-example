package com.microservices.product.mapper;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.UpdateProductRequest;
import com.microservices.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductDTO toDTO(Product product) {
        if (product == null) {
            return null;
        }
        
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getStatus(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public Product toEntity(CreateProductRequest request) {
        if (request == null) {
            return null;
        }
        
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        
        return product;
    }

    public void updateEntityFromRequest(UpdateProductRequest request, Product product) {
        if (request == null || product == null) {
            return;
        }
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
        
        // Explicitly set updatedAt to ensure version increment
        product.setUpdatedAt(java.time.LocalDateTime.now());
    }
}