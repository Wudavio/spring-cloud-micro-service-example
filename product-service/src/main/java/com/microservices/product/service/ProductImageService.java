package com.microservices.product.service;

import com.microservices.product.dto.ProductImageDTO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageDTO> listImages(Long productId);

    List<ProductImageDTO> uploadImages(Long productId, List<MultipartFile> files);

    ProductImageDTO getImage(Long productId, Long imageId);

    Resource loadImageContent(Long productId, Long imageId);

    String getContentType(Long productId, Long imageId);

    void deleteImage(Long productId, Long imageId);

    void deleteAllForProduct(Long productId);
}
