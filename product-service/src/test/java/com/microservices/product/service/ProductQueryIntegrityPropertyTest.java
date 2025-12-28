package com.microservices.product.service;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.spring.JqwikSpringSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: microservices-order-inventory, Property 10: 產品查詢完整性
 * Validates: Requirements 2.4
 */
@JqwikSpringSupport
@SpringBootTest(classes = com.microservices.product.ProductServiceApplication.class)
@ActiveProfiles("test")
@Transactional
public class ProductQueryIntegrityPropertyTest {

    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data if needed
    }

    @Property(tries = 10)
    @Label("Property 10: 產品查詢完整性 - For any product query request, returned data should include name, description, price, category and all necessary fields")
    void productQueryShouldReturnCompleteInformation(
            @ForAll @NotBlank @StringLength(min = 1, max = 200) String name,
            @ForAll @StringLength(max = 200) String description,
            @ForAll @BigRange(min = "0.01", max = "999999.99") BigDecimal price,
            @ForAll @StringLength(max = 50) String category) {
        
        // Create a product first
        CreateProductRequest createRequest = new CreateProductRequest(
                name + "_" + System.nanoTime(),
                description,
                price,
                category
        );

        ProductDTO createdProduct = productService.createProduct(createRequest);
        assertThat(createdProduct).isNotNull();
        assertThat(createdProduct.getId()).isNotNull();

        // Query the product by ID
        Optional<ProductDTO> queriedProduct = productService.getProductById(createdProduct.getId());
        
        // Verify query integrity - all necessary fields should be present
        assertThat(queriedProduct).isPresent();
        ProductDTO product = queriedProduct.get();
        
        // Verify all necessary fields are present and correct
        assertThat(product.getId()).isNotNull().isEqualTo(createdProduct.getId());
        assertThat(product.getName()).isNotNull().isEqualTo(createRequest.getName());
        assertThat(product.getDescription()).isEqualTo(createRequest.getDescription());
        assertThat(product.getPrice()).isNotNull().isEqualTo(createRequest.getPrice());
        assertThat(product.getCategory()).isEqualTo(createRequest.getCategory());
        assertThat(product.getStatus()).isNotNull();
        assertThat(product.getVersion()).isNotNull();
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Property(tries = 10)
    @Label("Property 10: 產品查詢完整性 - Active product queries should only return active products")
    void activeProductQueryShouldOnlyReturnActiveProducts(
            @ForAll @NotBlank @StringLength(min = 1, max = 200) String name,
            @ForAll @BigRange(min = "0.01", max = "999999.99") BigDecimal price) {
        
        // Create a product (should be active by default)
        CreateProductRequest createRequest = new CreateProductRequest(
                name + "_" + System.nanoTime(),
                "Test description for active query",
                price,
                "Test category"
        );

        ProductDTO createdProduct = productService.createProduct(createRequest);
        assertThat(createdProduct).isNotNull();
        
        // Query active product by ID
        Optional<ProductDTO> activeProduct = productService.getActiveProductById(createdProduct.getId());
        
        // Should return the product since it's active
        assertThat(activeProduct).isPresent();
        assertThat(activeProduct.get().getId()).isEqualTo(createdProduct.getId());
        assertThat(activeProduct.get().getStatus().name()).isEqualTo("ACTIVE");
        
        // Verify that the active product query returns complete information
        ProductDTO product = activeProduct.get();
        assertThat(product.getName()).isNotNull();
        assertThat(product.getPrice()).isNotNull();
        assertThat(product.getStatus()).isNotNull();
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Property(tries = 10)
    @Label("Property 10: 產品查詢完整性 - Query consistency across different access methods")
    void queryConsistencyAcrossDifferentMethods(
            @ForAll @NotBlank @StringLength(min = 1, max = 200) String name,
            @ForAll @BigRange(min = "0.01", max = "999999.99") BigDecimal price) {
        
        // Create a product
        CreateProductRequest createRequest = new CreateProductRequest(
                name + "_" + System.nanoTime(),
                "Test description for consistency",
                price,
                "Test category"
        );

        ProductDTO createdProduct = productService.createProduct(createRequest);
        assertThat(createdProduct).isNotNull();
        
        // Query the same product using different methods
        Optional<ProductDTO> productById = productService.getProductById(createdProduct.getId());
        Optional<ProductDTO> activeProductById = productService.getActiveProductById(createdProduct.getId());
        boolean existsById = productService.existsById(createdProduct.getId());
        boolean isActive = productService.isProductActive(createdProduct.getId());
        
        // All queries should be consistent
        assertThat(productById).isPresent();
        assertThat(activeProductById).isPresent();
        assertThat(existsById).isTrue();
        assertThat(isActive).isTrue();
        
        // The returned products should have the same data
        assertThat(productById.get().getId()).isEqualTo(activeProductById.get().getId());
        assertThat(productById.get().getName()).isEqualTo(activeProductById.get().getName());
        assertThat(productById.get().getPrice()).isEqualTo(activeProductById.get().getPrice());
        assertThat(productById.get().getStatus()).isEqualTo(activeProductById.get().getStatus());
    }

    @Property(tries = 10)
    @Label("Property 10: 產品查詢完整性 - Non-existent product queries should return empty consistently")
    void nonExistentProductQueriesShouldReturnEmptyConsistently(@ForAll("nonExistentIds") Long nonExistentId) {
        
        // Query non-existent product using different methods
        Optional<ProductDTO> productById = productService.getProductById(nonExistentId);
        Optional<ProductDTO> activeProductById = productService.getActiveProductById(nonExistentId);
        boolean existsById = productService.existsById(nonExistentId);
        boolean isActive = productService.isProductActive(nonExistentId);
        
        // All queries should consistently indicate the product doesn't exist
        assertThat(productById).isEmpty();
        assertThat(activeProductById).isEmpty();
        assertThat(existsById).isFalse();
        assertThat(isActive).isFalse();
    }

    @Provide
    Arbitrary<Long> nonExistentIds() {
        return Arbitraries.longs().between(999999L, 9999999L);
    }
}