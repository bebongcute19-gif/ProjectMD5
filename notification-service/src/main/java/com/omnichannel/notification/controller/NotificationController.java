package com.omnichannel.notification.controller;

import com.omnichannel.notification.entity.NotificationLog;
import com.omnichannel.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<?> getLatestNotifications(
            @RequestParam(required = false) String email,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {
        
        boolean isAdmin = "ADMIN".equalsIgnoreCase(headerRole);
        List<NotificationLog> list;

        if (isAdmin) {
            // Quản trị viên (ADMIN) có quyền xem toàn bộ 50 thông báo gần nhất hoặc lọc theo email
            if (email != null && !email.isBlank()) {
                list = notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email);
            } else {
                list = notificationRepository.findTop50ByOrderByCreatedAtDesc();
            }
        } else {
            // Khách hàng (CUSTOMER) chỉ được xem thông báo của chính mình (chống lộ thông báo người khác)
            String userEmail = (headerEmail != null && !headerEmail.isBlank()) 
                    ? headerEmail 
                    : (email != null && !email.isBlank() ? email : "");

            if (userEmail.isBlank()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Yêu cầu đăng nhập hoặc chỉ định email tài khoản hợp lệ để xem thông báo cá nhân."
                ));
            }
            list = notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(userEmail);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "count", list.size(),
            "data", list
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "notification-service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
