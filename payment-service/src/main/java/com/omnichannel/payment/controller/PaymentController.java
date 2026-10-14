package com.omnichannel.payment.controller;

import com.omnichannel.payment.dto.PaymentResponseDto;
import com.omnichannel.payment.dto.ProcessPaymentRequest;
import com.omnichannel.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processPayment(
            @RequestBody ProcessPaymentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        try {
            // Ownership validation: normal customer can only pay for their own order
            if (userId != null && !"ADMIN".equalsIgnoreCase(role)) {
                paymentService.validatePaymentOwnership(request.getOrderId(), userId);
            }

            PaymentResponseDto response = paymentService.processPayment(request);
            boolean isSuccess = !"FAIL".equalsIgnoreCase(request.getAction());
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "success", isSuccess,
                "message", isSuccess ? "Thanh toán thành công! Sự kiện đã gửi qua RabbitMQ." : "Thanh toán thất bại! Đã kích hoạt Saga bồi hoàn hoàn kho.",
                "data", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", "Lỗi xử lý thanh toán: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getPaymentByOrderId(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        try {
            PaymentResponseDto response = paymentService.getPaymentByOrderId(orderId);
            if (userId != null && !"ADMIN".equalsIgnoreCase(role)) {
                if (response.getCustomerId() != null && !response.getCustomerId().equals(userId)) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
                        "code", 403,
                        "success", false,
                        "message", "Bạn không có quyền xem thông tin giao dịch của người khác."
                    ));
                }
            }
            return ResponseEntity.ok(Map.of("code", 200, "success", true, "data", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllPayments(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "success", false,
                "message", "Yêu cầu quyền ADMIN để xem toàn bộ danh sách giao dịch thanh toán."
            ));
        }
        List<PaymentResponseDto> list = paymentService.getAllPayments();
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "success", true,
            "count", list.size(),
            "data", list
        ));
    }

    @GetMapping("/vnpay-url")
    public ResponseEntity<?> getVNPayUrl(@RequestParam Long orderId, @RequestParam(required = false) BigDecimal amount) {
        String url = paymentService.generateVNPaySimulationUrl(orderId, amount);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "paymentUrl", url,
            "orderId", orderId
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "payment-service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
