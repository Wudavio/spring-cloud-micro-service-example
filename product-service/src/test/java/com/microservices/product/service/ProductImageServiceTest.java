package com.microservices.product.service;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.ProductImageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.microservices.product.ProductServiceApplication.class)
@ActiveProfiles("test")
class ProductImageServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductImageService productImageService;

    @Test
    void uploadsMultipleImagesAndEnforcesMaxTen() {
        ProductDTO product = productService.createProduct(new CreateProductRequest(
                "Image Product " + System.nanoTime(),
                "desc",
                new BigDecimal("19.99"),
                "Gadgets"
        ));

        List<MultipartFile> firstBatch = List.of(
                jpeg("a.jpg"),
                jpeg("b.jpg"),
                png("c.png")
        );
        List<ProductImageDTO> saved = productImageService.uploadImages(product.getId(), firstBatch);
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getUrl()).contains("/products/" + product.getId() + "/images/");

        List<MultipartFile> fillToTen = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            fillToTen.add(jpeg("more-" + i + ".jpg"));
        }
        assertThat(productImageService.uploadImages(product.getId(), fillToTen)).hasSize(7);
        assertThat(productImageService.listImages(product.getId())).hasSize(10);

        assertThatThrownBy(() -> productImageService.uploadImages(product.getId(), List.of(jpeg("overflow.jpg"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 10");

        ProductDTO reloaded = productService.getProductById(product.getId()).orElseThrow();
        assertThat(reloaded.getImages()).hasSize(10);

        Long imageId = reloaded.getImages().get(0).getId();
        productImageService.deleteImage(product.getId(), imageId);
        assertThat(productImageService.listImages(product.getId())).hasSize(9);
    }

    @Test
    void rejectsUnsupportedContentType() {
        ProductDTO product = productService.createProduct(new CreateProductRequest(
                "Bad Type Product " + System.nanoTime(),
                "desc",
                new BigDecimal("9.99"),
                "Gadgets"
        ));
        MultipartFile pdf = new MockMultipartFile(
                "files", "note.pdf", "application/pdf", new byte[]{1, 2, 3, 4});
        assertThatThrownBy(() -> productImageService.uploadImages(product.getId(), List.of(pdf)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported image content type");
    }

    private static MultipartFile jpeg(String name) {
        // Minimal JPEG-like payload; content-type is what we validate for v1.
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }

    private static MultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2});
    }
}
