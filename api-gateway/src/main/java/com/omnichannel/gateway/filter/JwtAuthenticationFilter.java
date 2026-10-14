package com.omnichannel.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final Set<String> HEALTH_PATHS = Set.of(
            "/api/v1/auth/health",
            "/api/v1/products/health",
            "/api/v1/orders/health",
            "/api/v1/payments/health",
            "/api/v1/notifications/health"
    );

    @Value("${jwt.secret:omnichannel-ecommerce-microservices-super-secure-secret-key-2026-very-long-key-32bytes}")
    private String jwtSecret;

    @Value("${rate.limit.max:10000}")
    private int maxRequestsPerWindow;

    @Value("${rate.limit.window.ms:10000}")
    private long windowDurationMs;

    @Value("${rate.limit.enabled:true}")
    private boolean rateLimitEnabled;

    // In-memory sliding window rate limiter per client IP
    private final Map<String, RequestCounter> rateLimitMap = new ConcurrentHashMap<>();

    private static class RequestCounter {
        long windowStart = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger(0);
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. Rate Limiting Check (Configurable via RATE_LIMIT_MAX)
        if (rateLimitEnabled) {
            String clientIp = getClientIp(request);
            if (isRateLimited(clientIp)) {
                log.warn(">>> [Gateway RateLimit] IP {} exceeded rate limit (>{}/{}ms) on {}",
                        clientIp, maxRequestsPerWindow, windowDurationMs, path);
                return onError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu. Vui lòng thử lại sau giây lát (Rate Limit Exceeded).");
            }
        }

        // 2. Remove any spoofed client headers (security hardening)
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                });

        // 3. Determine if path is public
        if (isPublicPath(path, method)) {
            // If client optionally provides token on public endpoint, still parse for context
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    Claims claims = parseClaims(authHeader.substring(7));
                    attachUserHeaders(requestBuilder, claims);
                } catch (Exception ignored) {}
            }
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 4. Secured Path: Require valid JWT token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn(">>> [Gateway Auth] Missing Authorization header on secured path: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục (Thiếu token Authorization).");
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (Exception e) {
            log.warn(">>> [Gateway Auth] Invalid/Expired JWT on path {}: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        }

        String role = claims.get("role") != null ? claims.get("role").toString() : "CUSTOMER";

        // 5. Admin RBAC Verification
        if (isAdminPath(path, method)) {
            if (!"ADMIN".equalsIgnoreCase(role)) {
                log.warn(">>> [Gateway RBAC] User {} with role {} denied access to ADMIN resource: {}", claims.getSubject(), role, path);
                return onError(exchange, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này (Yêu cầu quyền ADMIN).");
            }
        }

        // 6. Forward verified user context downstream
        attachUserHeaders(requestBuilder, claims);
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/orders/inventory"))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && HEALTH_PATHS.contains(path)) {
            return true;
        }
        if (path.startsWith("/actuator") || path.equals("/favicon.ico")) {
            return true;
        }
        return false;
    }

    private boolean isAdminPath(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/orders/admin") || path.startsWith("/api/v1/orders/benchmark")) {
            return true;
        }
        if (path.startsWith("/api/v1/orders/") && path.endsWith("/status") && HttpMethod.PUT.equals(method)) {
            return true;
        }
        if (path.startsWith("/api/v1/products") && (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method))) {
            return true;
        }
        // Protect global payment audit log from normal users
        if (path.equals("/api/v1/payments") && HttpMethod.GET.equals(method)) {
            return true;
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private void attachUserHeaders(ServerHttpRequest.Builder builder, Claims claims) {
        builder.header("X-User-Id", claims.getSubject())
               .header("X-User-Email", claims.get("email") != null ? claims.get("email").toString() : "")
               .header("X-User-Role", claims.get("role") != null ? claims.get("role").toString() : "CUSTOMER");
    }

    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();

        // Memory leak prevention: Evict expired window entries if cache grows
        if (rateLimitMap.size() > 500) {
            rateLimitMap.entrySet().removeIf(e -> (now - e.getValue().windowStart) > windowDurationMs * 2);
        }

        RequestCounter counter = rateLimitMap.compute(clientIp, (ip, current) -> {
            if (current == null || (now - current.windowStart) > windowDurationMs) {
                RequestCounter reset = new RequestCounter();
                reset.windowStart = now;
                reset.count.set(1);
                return reset;
            } else {
                current.count.incrementAndGet();
                return current;
            }
        });
        return counter.count.get() > maxRequestsPerWindow;
    }

    private String getClientIp(ServerHttpRequest request) {
        // Prioritize Nginx authoritative X-Real-IP if present
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        // Direct socket connection
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return "127.0.0.1";
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"success\":false}", status.value(), message);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Execute before routing
    }
}
