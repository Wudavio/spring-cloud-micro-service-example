package com.microservices.product.service;

import com.microservices.product.config.ProductImageProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local filesystem storage under the product-service working directory.
 */
@Service
public class ProductImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageStorageService.class);
    private static final Set<String> SAFE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ProductImageProperties properties;
    private Path rootDir;

    public ProductImageStorageService(ProductImageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws IOException {
        rootDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(rootDir);
        log.info("Product image upload directory: {}", rootDir);
    }

    public Path productDirectory(Long productId) throws IOException {
        Path dir = rootDir.resolve(String.valueOf(productId)).normalize();
        if (!dir.startsWith(rootDir)) {
            throw new IllegalArgumentException("Invalid product image path");
        }
        Files.createDirectories(dir);
        return dir;
    }

    public StoredFile store(Long productId, MultipartFile file) throws IOException {
        String extension = resolveExtension(file);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = productDirectory(productId).resolve(storedName).normalize();
        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("Invalid product image path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(storedName, target, Files.size(target));
    }

    public Resource loadAsResource(Long productId, String storedFileName) throws IOException {
        Path file = productDirectory(productId).resolve(storedFileName).normalize();
        if (!file.startsWith(rootDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("Image file not found: " + storedFileName);
        }
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Image file not readable: " + storedFileName);
        }
        return resource;
    }

    public void deleteQuietly(Long productId, String storedFileName) {
        try {
            Path file = productDirectory(productId).resolve(storedFileName).normalize();
            if (file.startsWith(rootDir)) {
                Files.deleteIfExists(file);
            }
        } catch (Exception ex) {
            log.warn("Failed to delete image file productId={} file={}: {}",
                    productId, storedFileName, ex.getMessage());
        }
    }

    public void deleteProductDirectoryQuietly(Long productId) {
        try {
            Path dir = rootDir.resolve(String.valueOf(productId)).normalize();
            if (!dir.startsWith(rootDir) || !Files.isDirectory(dir)) {
                return;
            }
            try (var stream = Files.list(dir)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
            Files.deleteIfExists(dir);
        } catch (Exception ex) {
            log.warn("Failed to delete product image directory productId={}: {}", productId, ex.getMessage());
        }
    }

    private String resolveExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(original == null ? "" : original);
        if (ext != null && !ext.isBlank()) {
            ext = ext.toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSIONS.contains(ext)) {
                return "jpeg".equals(ext) ? "jpg" : ext;
            }
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    public record StoredFile(String storedFileName, Path path, long sizeBytes) {}
}
