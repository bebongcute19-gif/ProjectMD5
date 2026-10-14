package com.omnichannel.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.order.dto.*;
import com.omnichannel.order.entity.*;
import com.omnichannel.order.event.*;
import com.omnichannel.order.lock.RedisLockService;
import com.omnichannel.order.repository.InventoryRepository;
import com.omnichannel.order.repository.OrderRepository;
import com.omnichannel.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${product.service.url:http://product-service:8082}")
    private String productServiceUrl;

    // In-memory atomic counters for high-speed benchmark auditing
    private final AtomicInteger rejectedOrderCount = new AtomicInteger(0);
    private final AtomicInteger successfulFlashSaleCount = new AtomicInteger(0);

    public OrderService(OrderRepository orderRepository,
                        InventoryRepository inventoryRepository,
                        OutboxRepository outboxRepository,
                        OutboxPublisher outboxPublisher,
                        RedisLockService redisLockService,
                        PlatformTransactionManager transactionManager,
                        ObjectMapper objectMapper,
                        RestTemplateBuilder restTemplateBuilder) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.redisLockService = redisLockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(1500))
                .build();
    }

    /**
     * Internal Product Snapshot DTO to safeguard pricing integrity
     */
    private static class ProductSnapshot {
        Long id;
        String name;
        String sku;
        BigDecimal price;
        int stock;
        boolean exists;

        ProductSnapshot(Long id, String name, String sku, BigDecimal price, int stock, boolean exists) {
            this.id = id;
            this.name = name;
            this.sku = sku;
            this.price = price;
            this.stock = stock;
            this.exists = exists;
        }
    }

    private ProductSnapshot fetchProductSnapshot(Long productId) {
        try {
            String url = productServiceUrl + "/api/v1/products/" + productId;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && resp.containsKey("data") && resp.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                String name = (String) data.get("name");
                String sku = (String) data.get("sku");
                boolean flashSale = Boolean.TRUE.equals(data.get("flashSale"));
                int stock = data.get("stock") != null ? ((Number) data.get("stock")).intValue() : 0;

                BigDecimal price;
                if (flashSale && data.get("flashSalePrice") != null) {
                    price = new BigDecimal(data.get("flashSalePrice").toString());
                } else if (data.get("price") != null) {
                    price = new BigDecimal(data.get("price").toString());
                } else {
                    throw new IllegalStateException("Sản phẩm #" + productId + " chưa được thiết lập giá bán!");
                }
                return new ProductSnapshot(productId, name, sku, price, stock, true);
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Sản phẩm không tồn tại: " + productId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Sản phẩm không tồn tại: " + productId);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            log.error(">>> [Product Client] Connection error to Product Service for product #{}: {}", productId, e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ sản phẩm (Product Service) tạm thời không khả dụng, không thể xác định giá chính xác.");
        }
    }

    /**
     * Creates an order with:
     * 1. Multi-item sorting to prevent deadlocks
     * 2. Redis Lua pre-deduction FIRST for sub-millisecond rejects (avoids calling Product Service when sold out)
     * 3. Fetching authentic catalog snapshots only after stock availability is confirmed
     * 4. Single-point rollback in catch block (no double-revert bug)
     * 5. Atomic Conditional Update on Database
     * 6. Transactional Outbox Pattern with mandatory transactional rollback on failure
     */
    public OrderResponseDto createOrder(CreateOrderRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất 1 sản phẩm!");
        }
        for (OrderItemRequest item : req.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm #" + item.getProductId() + " phải lớn hơn 0!");
            }
        }

        // STEP 0: Idempotency Check - prevent double order creation and duplicate stock deductions
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            Optional<Order> existingOpt = orderRepository.findByIdempotencyKey(req.getIdempotencyKey().trim());
            if (existingOpt.isPresent()) {
                Order existing = existingOpt.get();
                // 1. Data leak protection: Verify that this idempotency key belongs to the requesting customer!
                if (req.getCustomerId() != null && !req.getCustomerId().equals(existing.getCustomerId())) {
                    log.warn(">>> [Idempotency] Security violation: User {} attempted to access order belonging to user {}",
                            req.getCustomerId(), existing.getCustomerId());
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT,
                            "Idempotency-Key đã được sử dụng bởi một tài khoản khác!");
                }
                // 2. Request payload mismatch verification (IETF Idempotency Standard)
                if (!isPayloadMatching(req, existing)) {
                    log.warn(">>> [Idempotency] Payload mismatch for Idempotency-Key {}: Request cart does not match original order.",
                            req.getIdempotencyKey());
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "Nội dung đơn hàng không khớp với yêu cầu trước đó sử dụng cùng Idempotency-Key!");
                }
                log.info(">>> [Idempotency] Intercepted duplicate order request with Idempotency-Key: {}. Returning existing Order #{}",
                        req.getIdempotencyKey(), existing.getOrderNumber());
                return new OrderResponseDto(existing);
            }
        }

        // STEP 1: Sort items by productId ascending -> Mathematically prevents Lock Deadlock!
        req.getItems().sort(Comparator.comparing(OrderItemRequest::getProductId));

        // STEP 2: Fast Redis Lua pre-deduct FIRST to filter out requests when sold out
        // Under high load, sold-out requests are rejected in sub-milliseconds WITHOUT making HTTP calls to Product Service!
        List<Long> preDeductedProductIds = new ArrayList<>();
        List<Integer> preDeductedQuantities = new ArrayList<>();

        for (OrderItemRequest item : req.getItems()) {
            int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            long redisStock = redisLockService.preDeductStockLua(item.getProductId(), qty);
            if (redisStock == -2) {
                // Key missing in Redis (Redis restarted or cache evicted) -> Rehydrate via SETNX to avoid race condition overwrite!
                log.warn(">>> [Redis Resilience] Key missing in Redis for Product #{}. Rehydrating from DB via SETNX...", item.getProductId());
                int dbStock = getOrInitDbStock(item.getProductId());
                redisLockService.setStockRedisIfAbsent(item.getProductId(), dbStock);
                redisStock = redisLockService.preDeductStockLua(item.getProductId(), qty);
            }
            if (redisStock == -1) {
                // Out of stock in Redis cache - rollback previously deducted items in this cart and reject
                rollbackPreDeductedStock(preDeductedProductIds, preDeductedQuantities);
                rejectedOrderCount.incrementAndGet();
                throw new IllegalStateException("HẾT HÀNG! Sản phẩm #" + item.getProductId() + " đã hết trong sự kiện Flash Sale.");
            }
            if (redisStock >= 0) {
                preDeductedProductIds.add(item.getProductId());
                preDeductedQuantities.add(qty);
            }
        }

        List<String> acquiredLocks = new ArrayList<>();
        Map<String, String> lockTokens = new HashMap<>();

        try {
            // STEP 3: Only fetch authentic product snapshots after stock is confirmed available in Redis!
            Map<Long, ProductSnapshot> snapshotMap = new HashMap<>();
            for (OrderItemRequest item : req.getItems()) {
                ProductSnapshot snapshot = fetchProductSnapshot(item.getProductId());
                snapshotMap.put(item.getProductId(), snapshot);
            }

            // STEP 4: Acquire Distributed Locks in strictly sorted order
            for (OrderItemRequest item : req.getItems()) {
                String lockKey = "lock:inventory:" + item.getProductId();
                String lockValue = UUID.randomUUID().toString();

                boolean acquired = redisLockService.acquireLock(lockKey, lockValue, 3000, 1000);
                if (!acquired) {
                    rejectedOrderCount.incrementAndGet();
                    throw new IllegalStateException("Hệ thống Flash Sale đang quá tải với sản phẩm #" + item.getProductId() + ". Vui lòng thử lại!");
                }
                acquiredLocks.add(lockKey);
                lockTokens.put(lockKey, lockValue);
            }

            // STEP 5: Execute atomic order creation and Outbox event write inside Transaction boundary
            Order savedOrder = transactionTemplate.execute(status -> executeOrderCreation(req, snapshotMap));

            successfulFlashSaleCount.incrementAndGet();
            return new OrderResponseDto(savedOrder);

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            rollbackPreDeductedStock(preDeductedProductIds, preDeductedQuantities);
            preDeductedProductIds.clear(); // Ensure rollback only occurs once!
            if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
                Optional<Order> existingOpt = orderRepository.findByIdempotencyKey(req.getIdempotencyKey().trim());
                if (existingOpt.isPresent()) {
                    Order existing = existingOpt.get();
                    if (req.getCustomerId() != null && !req.getCustomerId().equals(existing.getCustomerId())) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "Idempotency-Key đã được sử dụng bởi một tài khoản khác!");
                    }
                    if (!isPayloadMatching(req, existing)) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                                "Nội dung đơn hàng không khớp với yêu cầu trước đó sử dụng cùng Idempotency-Key!");
                    }
                    log.info(">>> [Idempotency] Concurrent duplicate resolved for Idempotency-Key: {}. Returning Order #{}",
                            req.getIdempotencyKey(), existing.getOrderNumber());
                    return new OrderResponseDto(existing);
                }
            }
            throw e;
        } catch (Exception e) {
            rollbackPreDeductedStock(preDeductedProductIds, preDeductedQuantities);
            throw e;
        } finally {
            // STEP 6: Always release all acquired Redis Distributed Locks safely
            for (String lockKey : acquiredLocks) {
                String token = lockTokens.get(lockKey);
                if (token != null) {
                    redisLockService.releaseLock(lockKey, token);
                }
            }
        }
    }

    private boolean isPayloadMatching(CreateOrderRequest req, Order order) {
        if (req.getItems() == null || order.getItems() == null) {
            return false;
        }
        if (req.getItems().size() != order.getItems().size()) {
            return false;
        }
        Map<Long, Integer> reqMap = req.getItems().stream()
                .collect(Collectors.toMap(
                        OrderItemRequest::getProductId,
                        i -> (i.getQuantity() != null && i.getQuantity() > 0) ? i.getQuantity() : 1,
                        Integer::sum
                ));
        Map<Long, Integer> orderMap = order.getItems().stream()
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity,
                        Integer::sum
                ));
        return reqMap.equals(orderMap);
    }

    private int getOrInitDbStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getAvailableStock)
                .orElseGet(() -> {
                    ProductSnapshot snapshot = fetchProductSnapshot(productId);
                    int initialStock = snapshot != null && snapshot.stock > 0 ? snapshot.stock : 0;
                    Inventory newInv = new Inventory(productId, initialStock);
                    return inventoryRepository.save(newInv).getAvailableStock();
                });
    }

    private void rollbackPreDeductedStock(List<Long> productIds, List<Integer> quantities) {
        for (int i = 0; i < productIds.size(); i++) {
            redisLockService.restoreStockRedis(productIds.get(i), quantities.get(i));
        }
    }

    private Order executeOrderCreation(CreateOrderRequest req, Map<Long, ProductSnapshot> snapshotMap) {
        Order order = new Order();
        String orderNumber = "ORD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 900 + 100);
        order.setOrderNumber(orderNumber);
        order.setCustomerId(req.getCustomerId() != null ? req.getCustomerId() : 1L);
        order.setCustomerEmail(req.getCustomerEmail() != null ? req.getCustomerEmail() : "customer@ecommerce.com");
        order.setCustomerName(req.getCustomerName() != null ? req.getCustomerName() : "Khách Mua Hàng");
        order.setShippingAddress(req.getShippingAddress() != null ? req.getShippingAddress() : "Toàn quốc");
        order.setPhone(req.getPhone() != null ? req.getPhone() : "0900000000");
        order.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "VNPAY");
        order.setNotes(req.getNotes());
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            order.setIdempotencyKey(req.getIdempotencyKey().trim());
        }
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : req.getItems()) {
            Long productId = itemReq.getProductId();
            int qty = itemReq.getQuantity() != null && itemReq.getQuantity() > 0 ? itemReq.getQuantity() : 1;
            ProductSnapshot snapshot = snapshotMap.get(productId);

            // Step A: Ensure Inventory row exists, initialized from authentic product catalog
            inventoryRepository.findByProductId(productId).orElseGet(() -> {
                int initialStock = snapshot != null && snapshot.stock > 0 ? snapshot.stock : 0;
                Inventory newInv = new Inventory(productId, initialStock);
                return inventoryRepository.save(newInv);
            });

            // Step B: Atomic conditional update on DB (WHERE available_stock >= :qty)
            int rowsUpdated = inventoryRepository.deductStockConditional(productId, qty);
            if (rowsUpdated == 0) {
                rejectedOrderCount.incrementAndGet();
                throw new IllegalStateException("HẾT HÀNG! Tồn kho sản phẩm #" + productId + " không đủ để đáp ứng yêu cầu đặt hàng (" + qty + ").");
            }

            OrderItem item = new OrderItem();
            item.setProductId(productId);
            item.setProductName(snapshot.name);
            item.setSku(snapshot.sku);
            item.setPrice(snapshot.price);
            item.setQuantity(qty);
            BigDecimal subtotal = snapshot.price.multiply(BigDecimal.valueOf(qty));
            item.setSubtotal(subtotal);

            order.addItem(item);
            total = total.add(subtotal);
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);

        // Step C: Write Outbox Event inside the SAME transaction (Transactional Outbox Pattern)
        saveOutboxEvent(savedOrder);

        return savedOrder;
    }

    private void saveOutboxEvent(Order order) {
        try {
            OrderCreatedEvent event = new OrderCreatedEvent();
            event.setOrderId(order.getId());
            event.setOrderNumber(order.getOrderNumber());
            event.setCustomerId(order.getCustomerId());
            event.setCustomerEmail(order.getCustomerEmail());
            event.setCustomerName(order.getCustomerName());
            event.setTotalAmount(order.getTotalAmount());
            event.setPaymentMethod(order.getPaymentMethod());

            List<OrderItemDto> items = order.getItems().stream().map(i ->
                    new OrderItemDto(i.getProductId(), i.getProductName(), i.getSku(), i.getPrice(), i.getQuantity(), i.getSubtotal())
            ).collect(Collectors.toList());
            event.setItems(items);

            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent("ORDER", order.getOrderNumber(), "ORDER_CREATED", payload);
            outboxRepository.save(outboxEvent);

            log.info(">>> [Transactional Outbox] Saved event for Order: #{}", order.getOrderNumber());
        } catch (Exception e) {
            log.error(">>> [Transactional Outbox] Failed to save OutboxEvent. Rolling back order transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi ghi nhận Transactional Outbox: " + e.getMessage(), e);
        }
    }

    /**
     * Saga Pattern: Handle Payment Completed Event with PENDING Guard, Amount Check & Atomic Settlement
     */
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info(">>> [Saga] Received PaymentCompletedEvent for Order ID: {}", event.getOrderId());
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();

                // State Guard: Only allow transition if order is currently PENDING!
                if (order.getStatus() != OrderStatus.PENDING) {
                    log.info(">>> [Saga] Bỏ qua PaymentCompletedEvent. Đơn hàng #{} đang ở trạng thái: {}", order.getOrderNumber(), order.getStatus());
                    return;
                }

                // Security check: Validate payment event amount against order total amount
                if (event.getAmount() != null && order.getTotalAmount() != null
                        && event.getAmount().compareTo(order.getTotalAmount()) != 0) {
                    log.error(">>> [Saga Security Alert] Số tiền thanh toán không khớp cho đơn hàng #{}! Expected: {}, Paid: {}. Đánh dấu FAILED & hoàn kho!",
                            order.getOrderNumber(), order.getTotalAmount(), event.getAmount());
                    order.setStatus(OrderStatus.FAILED);
                    order.setNotes("Cảnh báo bảo mật: Số tiền thanh toán không khớp (Thực trả: " + event.getAmount() + " VNĐ, Cần thanh toán: " + order.getTotalAmount() + " VNĐ)");
                    orderRepository.save(order);

                    // Restore reserved stock for fraud attempt
                    for (OrderItem item : order.getItems()) {
                        inventoryRepository.restoreStockAtomic(item.getProductId(), item.getQuantity());
                    }
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                for (OrderItem item : order.getItems()) {
                                    redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                                }
                            }
                        });
                    }
                    return;
                }

                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);

                // Release reserved stock atomically without @Version conflicts
                for (OrderItem item : order.getItems()) {
                    inventoryRepository.releaseReservedStockAtomic(item.getProductId(), item.getQuantity());
                    log.info(">>> [Saga] Order #{} confirmed! Product #{} reserved stock settled atomically.",
                            order.getOrderNumber(), item.getProductId());
                }
                log.info(">>> [Saga] Order #{} transitioned to CONFIRMED successfully!", order.getOrderNumber());
            } else {
                log.warn(">>> [Saga] Order ID {} not found in database!", event.getOrderId());
            }
        });
    }

    /**
     * Saga Pattern: Compensating Transaction when Payment Fails with PENDING Guard & Atomic Restoration
     */
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn(">>> [Saga Compensation] Received PaymentFailedEvent for Order ID: {}. Triggering rollback!", event.getOrderId());
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();

                // State Guard: Only allow rollback if order is currently PENDING!
                if (order.getStatus() != OrderStatus.PENDING) {
                    log.info(">>> [Saga Compensation] Bỏ qua PaymentFailedEvent. Đơn hàng #{} đang ở trạng thái: {}", order.getOrderNumber(), order.getStatus());
                    return;
                }

                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);

                // Compensating Transaction: Restore stock atomically in DB
                for (OrderItem item : order.getItems()) {
                    inventoryRepository.restoreStockAtomic(item.getProductId(), item.getQuantity());
                    log.info(">>> [Saga Compensation] Restored {} units of Product #{} atomically in DB!",
                            item.getQuantity(), item.getProductId());
                }

                // Restore Redis stock strictly after DB transaction commit
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (OrderItem item : order.getItems()) {
                                redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                            }
                            log.info(">>> [Saga Compensation] Restored Redis stock after commit for Order #{}", order.getOrderNumber());
                        }
                    });
                } else {
                    for (OrderItem item : order.getItems()) {
                        redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                    }
                }
            } else {
                log.warn(">>> [Saga Compensation] Order ID {} not found in database!", event.getOrderId());
            }
        });
    }

    public List<OrderResponseDto> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(OrderResponseDto::new).collect(Collectors.toList());
    }

    public List<OrderResponseDto> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(OrderResponseDto::new).collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<OrderResponseDto> getAllOrdersPaged(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable).map(OrderResponseDto::new);
    }

    public OrderResponseDto getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponseDto::new)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + id));
    }

    /**
     * State Machine: Enforces valid lifecycle transitions
     * PENDING -> CONFIRMED -> SHIPPING -> COMPLETED
     * PENDING / CONFIRMED -> CANCELLED (triggers stock restoration)
     */
    @Transactional
    public OrderResponseDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));
        OrderStatus current = order.getStatus();

        if (current == newStatus) {
            return new OrderResponseDto(order);
        }

        boolean allowed = false;
        if (current == OrderStatus.PENDING && (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED)) {
            allowed = true;
        } else if (current == OrderStatus.CONFIRMED && (newStatus == OrderStatus.SHIPPING || newStatus == OrderStatus.CANCELLED)) {
            allowed = true;
        } else if (current == OrderStatus.SHIPPING && newStatus == OrderStatus.COMPLETED) {
            allowed = true;
        }

        if (!allowed) {
            throw new IllegalStateException("Không thể chuyển trạng thái đơn hàng từ " + current + " sang " + newStatus + "!");
        }

        // Stock handling during manual status change
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                if (current == OrderStatus.CONFIRMED) {
                    // Confirmed order already settled reservedStock -> only restore availableStock!
                    inventoryRepository.restoreAvailableStockOnly(item.getProductId(), item.getQuantity());
                } else {
                    // Pending order had stock reserved -> restore availableStock and deduct reservedStock!
                    inventoryRepository.restoreStockAtomic(item.getProductId(), item.getQuantity());
                }
            }

            // Restore Redis stock strictly after DB commit
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (OrderItem item : order.getItems()) {
                            redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                        }
                        log.info(">>> [OrderService] Restored Redis stock after commit for Order #{}", order.getOrderNumber());
                    }
                });
            } else {
                for (OrderItem item : order.getItems()) {
                    redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                }
            }
            log.info(">>> [OrderService] Order #{} (previous: {}) cancelled by Admin, restored stock.", order.getOrderNumber(), current);
        } else if (current == OrderStatus.PENDING && newStatus == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryRepository.releaseReservedStockAtomic(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        log.info(">>> [OrderService] Order #{} transitioned from {} to {}", saved.getOrderNumber(), current, newStatus);
        return new OrderResponseDto(saved);
    }

    @Transactional
    public void resetBenchmark(Long productId, Integer targetStock) {
        int stock = targetStock != null ? targetStock : 10;
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> new Inventory(productId, stock));
        inv.setAvailableStock(stock);
        inv.setReservedStock(0);
        inventoryRepository.save(inv);

        // Sync Redis cache for Lua pre-deduct
        redisLockService.setStockRedis(productId, stock);

        rejectedOrderCount.set(0);
        successfulFlashSaleCount.set(0);
        log.info(">>> [Benchmark] Reset inventory for Product #{} to {}", productId, stock);
    }

    public BenchmarkStatsDto getBenchmarkStats(Long productId, Integer initialStock) {
        Inventory inv = inventoryRepository.findByProductId(productId).orElse(null);
        BenchmarkStatsDto stats = new BenchmarkStatsDto();
        stats.setProductId(productId);
        stats.setInitialStock(initialStock != null ? initialStock : 10);
        stats.setCurrentStock(inv != null ? inv.getAvailableStock() : 0);
        stats.setTotalOrdersPlaced(successfulFlashSaleCount.get() + rejectedOrderCount.get());
        stats.setSuccessfulFlashSaleOrders(successfulFlashSaleCount.get());
        stats.setRejectedOrders(rejectedOrderCount.get());
        stats.setRaceConditionDetected(inv != null && inv.getAvailableStock() < 0);
        return stats;
    }

    public BenchmarkStatsDto getBenchmarkStats(Long productId) {
        return getBenchmarkStats(productId, 10);
    }

    public Inventory getInventory(Long productId) {
        return inventoryRepository.findByProductId(productId).orElse(null);
    }

    @Transactional
    public Inventory updateInventoryStock(Long productId, Integer newStock) {
        if (newStock == null || newStock < 0) {
            throw new IllegalArgumentException("Số lượng tồn kho phải >= 0!");
        }
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> new Inventory(productId, newStock));
        inv.setAvailableStock(newStock);
        Inventory saved = inventoryRepository.save(inv);

        // Update Redis stock cache immediately for Lua pre-deduct
        redisLockService.setStockRedis(productId, newStock);
        log.info(">>> [OrderService Admin] Updated Inventory for Product #{}: Available stock = {} (DB + Redis)", productId, newStock);
        return saved;
    }
}
