package com.omnichannel.order.service;

import com.omnichannel.order.entity.Order;
import com.omnichannel.order.entity.OrderItem;
import com.omnichannel.order.entity.OrderStatus;
import com.omnichannel.order.lock.RedisLockService;
import com.omnichannel.order.repository.InventoryRepository;
import com.omnichannel.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisLockService redisLockService;
    private final TransactionTemplate transactionTemplate;

    public OrderTimeoutScheduler(OrderRepository orderRepository,
                                 InventoryRepository inventoryRepository,
                                 RedisLockService redisLockService,
                                 TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.redisLockService = redisLockService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Periodically check for expired PENDING orders (> 15 minutes) and trigger compensating cancellation
     */
    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds
    public void cleanupExpiredPendingOrders() {
        LocalDateTime expirationThreshold = LocalDateTime.now().minusMinutes(15);
        List<Order> expiredPendingOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, expirationThreshold);

        for (Order order : expiredPendingOrders) {
            cancelExpiredOrder(order);
        }
    }

    private void cancelExpiredOrder(Order order) {
        transactionTemplate.executeWithoutResult(status -> {
            try {
                // Re-fetch inside transaction
                Order currentOrder = orderRepository.findById(order.getId()).orElse(null);
                if (currentOrder == null || currentOrder.getStatus() != OrderStatus.PENDING) {
                    return;
                }

                currentOrder.setStatus(OrderStatus.CANCELLED);
                currentOrder.setNotes("Đơn hàng tự động hủy do quá hạn thanh toán 15 phút (Timeout Compensation).");
                orderRepository.save(currentOrder);

                // Restore reserved inventory back to available stock atomically in DB
                for (OrderItem item : currentOrder.getItems()) {
                    inventoryRepository.restoreStockAtomic(item.getProductId(), item.getQuantity());
                    log.info(">>> [Order Timeout Cleaner] Restored {} units of Product #{} atomically in DB from expired order #{}",
                            item.getQuantity(), item.getProductId(), currentOrder.getOrderNumber());
                }

                // Restore Redis stock strictly after DB commit
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (OrderItem item : currentOrder.getItems()) {
                                redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                            }
                            log.info(">>> [Order Timeout Cleaner] Restored Redis stock after commit for expired order #{}", currentOrder.getOrderNumber());
                        }
                    });
                } else {
                    for (OrderItem item : currentOrder.getItems()) {
                        redisLockService.restoreStockRedis(item.getProductId(), item.getQuantity());
                    }
                }

                log.warn(">>> [Order Timeout Cleaner] Successfully cancelled expired order #{} and released reserved stock!", currentOrder.getOrderNumber());
            } catch (Exception e) {
                log.error(">>> [Order Timeout Cleaner] Failed to cancel expired order #{}: {}", order.getOrderNumber(), e.getMessage(), e);
            }
        });
    }
}
