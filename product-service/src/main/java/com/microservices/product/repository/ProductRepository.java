package com.microservices.product.repository;

import com.microservices.product.entity.Product;
import com.microservices.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
    
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    
    Page<Product> findByCategory(String category, Pageable pageable);
    
    Page<Product> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Product p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "p.status = :status")
    Page<Product> searchByKeywordAndStatus(@Param("keyword") String keyword, 
                                          @Param("status") ProductStatus status, 
                                          Pageable pageable);
    
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
}