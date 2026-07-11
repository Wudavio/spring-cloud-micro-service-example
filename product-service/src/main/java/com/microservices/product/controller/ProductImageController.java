package com.microservices.product.controller;

import com.microservices.product.dto.ProductImageDTO;
import com.microservices.product.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/products/{productId}/images")
@Tag(name = "產品圖片", description = "產品多圖上傳／查詢／刪除（每產品最多 10 張，本機目錄儲存）")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @Operation(summary = "列出產品圖片", description = "依 sortOrder 回傳該產品所有圖片中繼資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductImageDTO.class)))),
            @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @GetMapping
    public ResponseEntity<List<ProductImageDTO>> list(
            @Parameter(description = "產品 ID") @PathVariable Long productId) {
        return ResponseEntity.ok(productImageService.listImages(productId));
    }

    @Operation(summary = "上傳產品圖片（多圖）",
            description = "multipart/form-data，欄位名 `files`。單次可傳多張；每產品合計最多 10 張。"
                    + "允許 JPEG/PNG/WebP/GIF。需 ADMIN。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "上傳成功",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProductImageDTO.class)))),
            @ApiResponse(responseCode = "400", description = "超過張數、格式或大小不符"),
            @ApiResponse(responseCode = "401", description = "未認證"),
            @ApiResponse(responseCode = "403", description = "非 ADMIN"),
            @ApiResponse(responseCode = "404", description = "產品不存在")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ProductImageDTO>> upload(
            @Parameter(description = "產品 ID") @PathVariable Long productId,
            @Parameter(description = "圖片檔案（可多個）", required = true)
            @RequestPart("files") MultipartFile[] files) {
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        List<ProductImageDTO> saved = productImageService.uploadImages(productId, list);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "取得單張圖片中繼資料")
    @GetMapping("/{imageId}")
    public ResponseEntity<ProductImageDTO> getMeta(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(productImageService.getImage(productId, imageId));
    }

    @Operation(summary = "下載／預覽圖片內容")
    @GetMapping("/{imageId}/content")
    public ResponseEntity<Resource> content(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        Resource resource = productImageService.loadImageContent(productId, imageId);
        String contentType = productImageService.getContentType(productId, imageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @Operation(summary = "刪除單張產品圖片", description = "需 ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "刪除成功"),
            @ApiResponse(responseCode = "404", description = "產品或圖片不存在")
    })
    @DeleteMapping("/{imageId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
