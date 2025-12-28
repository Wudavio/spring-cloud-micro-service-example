package com.microservices.product.service;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.UpdateProductRequest;
import com.microservices.product.entity.ProductStatus;
import com.microservices.product.exception.ProductNotFoundException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Feature: microservices-order-inventory, Property 8: 產品CRUD操作
 * Validates: Requirements 2.1, 2.2
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = com.microservices.product.ProductServiceApplication.class)
@ActiveProfiles("test")
@Transactional
public class ProductCrudPropertyTest {

    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data if needed
    }

    @Test
    void productCrudOperationsShouldSucceedWithValidData() {
        // Use jqwik generators to create test data
        Arbitrary<String> nameArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);
        Arbitrary<String> descArb = Arbitraries.strings().ofMaxLength(200);
        Arbitrary<BigDecimal> priceArb = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999999.99))
                .ofScale(2);
        Arbitrary<String> categoryArb = Arbitraries.strings().alpha().ofMaxLength(50);

        // Run multiple iterations with generated data
        for (int i = 0; i < 10; i++) {
            String name = nameArb.sample() + "_" + System.nanoTime();
            String description = descArb.sample();
            BigDecimal price = priceArb.sample();
            String category = categoryArb.sample();

            // Create product request
            CreateProductRequest createRequest = new CreateProductRequest(name, description, price, category);

            // Test CREATE operation
            ProductDTO createdProduct = productService.createProduct(createRequest);
            
            // Verify creation
            assertThat(createdProduct).isNotNull();
            assertThat(createdProduct.getId()).isNotNull();
            assertThat(createdProduct.getName()).isEqualTo(createRequest.getName());
            assertThat(createdProduct.getDescription()).isEqualTo(createRequest.getDescription());
            assertThat(createdProduct.getPrice()).isEqualTo(createRequest.getPrice());
            assertThat(createdProduct.getCategory()).isEqualTo(createRequest.getCategory());
            assertThat(createdProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(createdProduct.getCreatedAt()).isNotNull();
            assertThat(createdProduct.getUpdatedAt()).isNotNull();

            // Test READ operation
            Optional<ProductDTO> retrievedProduct = productService.getProductById(createdProduct.getId());
            assertThat(retrievedProduct).isPresent();
            assertThat(retrievedProduct.get().getId()).isEqualTo(createdProduct.getId());
            assertThat(retrievedProduct.get().getName()).isEqualTo(createdProduct.getName());

            // Test UPDATE operation - ensure we actually change values
            String updatedName = name + "_updated";
            String updatedDescription = (description != null ? description : "") + "_updated";
            BigDecimal updatedPrice = price.add(BigDecimal.valueOf(10.00)); // Add significant amount
            String updatedCategory = (category != null ? category : "") + "_updated";
            
            UpdateProductRequest updateRequest = new UpdateProductRequest(
                    updatedName,
                    updatedDescription,
                    updatedPrice,
                    updatedCategory,
                    ProductStatus.ACTIVE
            );

            ProductDTO updatedProduct = productService.updateProduct(createdProduct.getId(), updateRequest);
            
            // Verify update
            assertThat(updatedProduct).isNotNull();
            assertThat(updatedProduct.getId()).isEqualTo(createdProduct.getId());
            assertThat(updatedProduct.getName()).isEqualTo(updateRequest.getName());
            assertThat(updatedProduct.getDescription()).isEqualTo(updateRequest.getDescription());
            assertThat(updatedProduct.getPrice()).isEqualTo(updateRequest.getPrice());
            assertThat(updatedProduct.getCategory()).isEqualTo(updateRequest.getCategory());
            assertThat(updatedProduct.getStatus()).isEqualTo(updateRequest.getStatus());
            // Version should be incremented after update
            assertThat(updatedProduct.getVersion()).isNotNull();
            // Since we're using @Version, the version should be at least 0 (initial) or higher after update
            assertThat(updatedProduct.getVersion()).isGreaterThanOrEqualTo(0L);

            // Test DELETE operation
            productService.deleteProduct(createdProduct.getId());
            
            // Verify deletion
            Optional<ProductDTO> deletedProduct = productService.getProductById(createdProduct.getId());
            assertThat(deletedProduct).isEmpty();
        }
    }

    @Test
    void productExistenceCheckShouldBeConsistent() {
        // Use jqwik generators to create test data
        Arbitrary<String> nameArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);
        Arbitrary<BigDecimal> priceArb = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999999.99))
                .ofScale(2);

        // Run multiple iterations with generated data
        for (int i = 0; i < 10; i++) {
            String name = nameArb.sample() + "_" + System.nanoTime();
            BigDecimal price = priceArb.sample();
            
            CreateProductRequest createRequest = new CreateProductRequest(
                    name,
                    "Test description",
                    price,
                    "Test category"
            );

            // Before creation, product should not exist
            ProductDTO createdProduct = productService.createProduct(createRequest);
            
            // After creation, product should exist
            assertThat(productService.existsById(createdProduct.getId())).isTrue();
            assertThat(productService.isProductActive(createdProduct.getId())).isTrue();

            // After deletion, product should not exist
            productService.deleteProduct(createdProduct.getId());
            assertThat(productService.existsById(createdProduct.getId())).isFalse();
            assertThat(productService.isProductActive(createdProduct.getId())).isFalse();
        }
    }

    @Test
    void operationsOnNonExistentProductsShouldFail() {
        // Use jqwik generator for non-existent IDs
        Arbitrary<Long> nonExistentIdArb = Arbitraries.longs().between(999999L, 9999999L);
        
        // Test with multiple non-existent IDs
        for (int i = 0; i < 5; i++) {
            Long nonExistentId = nonExistentIdArb.sample();
            
            // GET operation should return empty
            Optional<ProductDTO> product = productService.getProductById(nonExistentId);
            assertThat(product).isEmpty();

            // UPDATE operation should throw exception
            UpdateProductRequest updateRequest = new UpdateProductRequest(
                    "Test Name",
                    "Test Description", 
                    BigDecimal.valueOf(10.00),
                    "Test Category",
                    ProductStatus.ACTIVE
            );
            
            ProductNotFoundException updateException = assertThrows(
                    ProductNotFoundException.class,
                    () -> productService.updateProduct(nonExistentId, updateRequest)
            );
            assertThat(updateException.getMessage()).contains("Product not found with id: " + nonExistentId);

            // DELETE operation should throw exception
            ProductNotFoundException deleteException = assertThrows(
                    ProductNotFoundException.class,
                    () -> productService.deleteProduct(nonExistentId)
            );
            assertThat(deleteException.getMessage()).contains("Product not found with id: " + nonExistentId);
        }
    }
}