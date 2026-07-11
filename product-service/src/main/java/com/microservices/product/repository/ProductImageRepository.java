package com.microservices.product.repository;

import com.microservices.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    long countByProductId(Long productId);

    void deleteByProductId(Long productId);

    @Query("select coalesce(max(i.sortOrder), -1) from ProductImage i where i.productId = :productId")
    int findMaxSortOrder(@Param("productId") Long productId);
}
