package com.omnichannel.order.controller;

import com.omnichannel.order.dto.BenchmarkStatsDto;
import com.omnichannel.order.dto.CreateOrderRequest;
import com.omnichannel.order.dto.OrderResponseDto;
import com.omnichannel.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        // Authoritative context from API Gateway (Zero Trust client-submitted customerId)
        if (userId != null) {
            request.setCustomerId(userId);
        }
        if (userEmail != null && !userEmail.isBlank()) {
            request.setCustomerEmail(userEmail);
        }
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader.trim());
        }

        try {
            OrderResponseDto order = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "code", 201,
                "success", true,
                "message", "Đặt hàng thành công! Kho đã được giữ an toàn.",
                "data", order
            ));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "code", e.getStatusCode().value(),
                "success", false,
                "message", e.getReason() != null ? e.getReason() : e.getMessage()
            ));
        } catch (IllegalStateException e) {
            // Flash Sale / Out of stock error
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", 409,
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", "Lỗi tạo đơn hàng: " + e.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<?> getCustomerOrders(@RequestParam(required = false) Long customerId,
                                               @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
                                               @RequestHeader(value = "X-User-Role", required = false) String role) {
        Long targetId = headerUserId != null ? headerUserId : (customerId != null ? customerId : 1L);

        // Security check: Customer can only view their own orders unless ADMIN
        if (headerUserId != null && customerId != null && !headerUserId.equals(customerId) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Bạn chỉ có thể xem danh sách đơn hàng của chính mình."
            ));
        }

        List<OrderResponseDto> orders = orderService.getOrdersByCustomer(targetId);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "count", orders.size(),
            "data", orders
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        try {
            OrderResponseDto order = orderService.getOrderById(id);

            // Data Leak Protection: Order ownership check
            if (userId != null && !"ADMIN".equalsIgnoreCase(role)) {
                if (!order.getCustomerId().equals(userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", 403,
                        "success", false,
                        "message", "Bạn không có quyền xem đơn hàng của người khác."
                    ));
                }
            }

            return ResponseEntity.ok(Map.of("code", 200, "success", true, "data", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", 404,
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0) page = 0;
        if (size < 1) size = 10;
        if (size > 100) size = 100; // Protection against excessive memory allocation / full table dumps

        org.springframework.data.domain.Page<OrderResponseDto> pagedOrders = orderService.getAllOrdersPaged(page, size);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "count", pagedOrders.getNumberOfElements(),
            "totalElements", pagedOrders.getTotalElements(),
            "totalPages", pagedOrders.getTotalPages(),
            "page", page,
            "size", size,
            "data", pagedOrders.getContent()
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Chỉ quản trị viên (ADMIN) mới có quyền cập nhật trạng thái đơn hàng."
            ));
        }

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", "Vui lòng truyền trạng thái hợp lệ (status: PENDING, CONFIRMED, PAID, SHIPPING, COMPLETED, CANCELLED, FAILED)."
            ));
        }

        try {
            com.omnichannel.order.entity.OrderStatus newStatus = com.omnichannel.order.entity.OrderStatus.valueOf(statusStr.toUpperCase());
            OrderResponseDto updated = orderService.updateOrderStatus(id, newStatus);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "success", true,
                "message", "Cập nhật trạng thái đơn hàng thành công!",
                "data", updated
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", "Chuyển trạng thái '" + statusStr + "' không hợp lệ: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/benchmark/reset-stock")
    public ResponseEntity<?> resetStockForBenchmark(
            @RequestParam(defaultValue = "1") Long productId,
            @RequestParam(defaultValue = "10") Integer stock) {
        orderService.resetBenchmark(productId, stock);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "message", "Đã reset tồn kho sản phẩm #" + productId + " về " + stock + " để chạy Benchmark / Load Test!",
            "productId", productId,
            "stock", stock
        ));
    }

    @GetMapping("/benchmark/stats")
    public ResponseEntity<?> getBenchmarkStats(
            @RequestParam(defaultValue = "1") Long productId,
            @RequestParam(defaultValue = "10") Integer initialStock) {
        BenchmarkStatsDto stats = orderService.getBenchmarkStats(productId, initialStock);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "data", stats
        ));
    }

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<?> getInventoryByProductId(@PathVariable Long productId) {
        com.omnichannel.order.entity.Inventory inv = orderService.getInventory(productId);
        if (inv == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", 404,
                "success", false,
                "message", "Sản phẩm #" + productId + " chưa có thông tin tồn kho."
            ));
        }
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "productId", productId,
            "availableStock", inv.getAvailableStock(),
            "reservedStock", inv.getReservedStock()
        ));
    }

    @PutMapping("/admin/inventory/{productId}")
    public ResponseEntity<?> updateInventoryStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Chỉ quản trị viên (ADMIN) mới có quyền điều chỉnh tồn kho."
            ));
        }
        Integer stock = body.get("stock");
        if (stock == null || stock < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", "Vui lòng truyền số lượng tồn kho hợp lệ không âm (yêu cầu stock >= 0)."
            ));
        }
        com.omnichannel.order.entity.Inventory updated = orderService.updateInventoryStock(productId, stock);
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "message", "Cập nhật tồn kho sản phẩm #" + productId + " thành công (DB + Redis)!",
            "data", Map.of(
                "productId", productId,
                "availableStock", updated.getAvailableStock(),
                "reservedStock", updated.getReservedStock()
            )
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "order-service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
