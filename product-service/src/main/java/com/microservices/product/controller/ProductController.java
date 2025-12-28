package com.microservices.product.controller;

import com.microservices.product.dto.CreateProductRequest;
import com.microservices.product.dto.ProductDTO;
import com.microservices.product.dto.UpdateProductRequest;
import com.microservices.product.entity.ProductStatus;
import com.microservices.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
@Tag(name = "產品管理", description = "產品 CRUD 操作、狀態管理和查詢功能")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "查詢產品列表", description = "支援分頁、分類篩選、關鍵字搜尋和狀態篩選")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Page.class)))
    })
    @GetMapping
    public ResponseEntity<Page<ProductDTO>> getProducts(
            @Parameter(description = "產品分類") @RequestParam(required = false) String category,
            @Parameter(description = "搜尋關鍵字") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否只顯示啟用產品") @RequestParam(defaultValue = "true") boolean activeOnly,
            @Parameter(description = "分頁參數") Pageable pageable) {
        
        Page<ProductDTO> products;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            products = productService.searchProducts(keyword.trim(), pageable);
        } else if (category != null && !category.trim().isEmpty()) {
            products = productService.getProductsByCategory(category.trim(), pageable);
        } else if (activeOnly) {
            products = productService.getActiveProducts(pageable);
        } else {
            products = productService.getAllProducts(pageable);
        }
        
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "查詢單一產品", description = "根據產品 ID 查詢產品詳細資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "查詢成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ProductDTO.class))),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(
            @Parameter(description = "產品 ID") @PathVariable Long id,
            @Parameter(description = "是否只查詢啟用產品") @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        Optional<ProductDTO> product = activeOnly 
            ? productService.getActiveProductById(id)
            : productService.getProductById(id);
            
        return product.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "創建產品", description = "創建新產品")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "創建成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ProductDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數錯誤")
    })
    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(
            @Parameter(description = "產品創建請求") @Valid @RequestBody CreateProductRequest request) {
        try {
            ProductDTO createdProduct = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "更新產品", description = "更新產品資訊")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = ProductDTO.class))),
        @ApiResponse(responseCode = "400", description = "請求參數錯誤"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @Parameter(description = "產品 ID") @PathVariable Long id, 
            @Parameter(description = "產品更新請求") @Valid @RequestBody UpdateProductRequest request) {
        try {
            ProductDTO updatedProduct = productService.updateProduct(id, request);
            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "刪除產品", description = "刪除產品（軟刪除）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "刪除成功"),
        @ApiResponse(responseCode = "400", description = "刪除失敗，可能存在關聯資料"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "產品 ID") @PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "變更產品狀態", description = "變更產品的啟用/停用狀態")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "狀態變更成功"),
        @ApiResponse(responseCode = "400", description = "狀態變更失敗"),
        @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeProductStatus(
            @Parameter(description = "產品 ID") @PathVariable Long id,
            @Parameter(description = "新狀態") @RequestParam ProductStatus status) {
        try {
            productService.changeProductStatus(id, status);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "搜尋產品", description = "根據關鍵字搜尋產品")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "搜尋成功",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Page.class))),
        @ApiResponse(responseCode = "400", description = "搜尋關鍵字不能為空")
    })
    @GetMapping("/search")
    public ResponseEntity<Page<ProductDTO>> searchProducts(
            @Parameter(description = "搜尋關鍵字") @RequestParam String keyword, 
            @Parameter(description = "分頁參數") Pageable pageable) {
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Page<ProductDTO> products = productService.searchProducts(keyword.trim(), pageable);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "檢查產品是否存在", description = "檢查指定 ID 的產品是否存在")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "檢查完成",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Boolean.class)))
    })
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> checkProductExists(
            @Parameter(description = "產品 ID") @PathVariable Long id) {
        boolean exists = productService.existsById(id);
        return ResponseEntity.ok(exists);
    }

    @Operation(summary = "檢查產品是否啟用", description = "檢查指定 ID 的產品是否為啟用狀態")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "檢查完成",
                content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = Boolean.class)))
    })
    @GetMapping("/{id}/active")
    public ResponseEntity<Boolean> checkProductActive(
            @Parameter(description = "產品 ID") @PathVariable Long id) {
        boolean active = productService.isProductActive(id);
        return ResponseEntity.ok(active);
    }
}