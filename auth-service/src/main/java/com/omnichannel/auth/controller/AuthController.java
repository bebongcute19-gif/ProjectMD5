package com.omnichannel.auth.controller;

import com.omnichannel.auth.dto.*;
import com.omnichannel.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "code", 201,
                "success", true,
                "message", "Đăng ký tài khoản thành công!",
                "data", response
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500,
                "success", false,
                "message", "Lỗi hệ thống: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "success", true,
                "message", "Đăng nhập thành công!",
                "data", response
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500,
                "success", false,
                "message", "Lỗi đăng nhập: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Authorization header thiếu hoặc không hợp lệ"
            ));
        }

        try {
            UserDTO user = authService.validateTokenAndGetUser(token);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "valid", true,
                "user", user
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "valid", false,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
                                        @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = headerUserId;
            if (userId == null && token != null) {
                userId = authService.validateTokenAndGetUser(token).getId();
            }
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Chưa xác thực"));
            }

            UserDTO profile = authService.getProfile(userId);
            return ResponseEntity.ok(Map.of("success", true, "data", profile));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "service", "auth-service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
