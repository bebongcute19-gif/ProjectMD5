package com.omnichannel.product.controller;

import com.omnichannel.product.dto.ProductDTO;
import com.omnichannel.product.dto.ProductRequest;
import com.omnichannel.product.entity.Category;
import com.omnichannel.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean flashSale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0) page = 0;
        if (size < 1) size = 10;
        if (size > 100) size = 100; // Protection against excessive memory allocation / full table dumps

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<ProductDTO> paged = productService.getProductsPaged(category, keyword, flashSale, pageable);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "count", paged.getNumberOfElements(),
            "totalElements", paged.getTotalElements(),
            "totalPages", paged.getTotalPages(),
            "page", page,
            "size", size,
            "data", paged.getContent()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        try {
            ProductDTO product = productService.getProductById(id);
            return ResponseEntity.ok(Map.of("code", 200, "success", true, "data", product));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", 404,
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<Category> categories = productService.getAllCategories();
        return ResponseEntity.ok(Map.of("code", 200, "success", true, "data", categories));
    }

    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request,
                                           @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Yêu cầu quyền ADMIN để thêm mới sản phẩm."
            ));
        }
        try {
            ProductDTO created = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "code", 201,
                "success", true,
                "data", created
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<?> getStock(@PathVariable Long id) {
        try {
            Integer stock = productService.getStock(id);
            return ResponseEntity.ok(Map.of("code", 200, "success", true, "productId", id, "stock", stock));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", 404, "success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<?> updateStock(@PathVariable Long id,
                                         @RequestParam Integer quantityChange,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Yêu cầu quyền ADMIN để cập nhật kho sản phẩm."
            ));
        }
        try {
            ProductDTO updated = productService.updateStock(id, quantityChange);
            return ResponseEntity.ok(Map.of("code", 200, "success", true, "data", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "product-service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
