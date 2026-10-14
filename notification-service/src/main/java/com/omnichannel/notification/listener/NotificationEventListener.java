package com.omnichannel.notification.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.notification.config.RabbitMQConfig;
import com.omnichannel.notification.entity.NotificationLog;
import com.omnichannel.notification.entity.ProcessedEvent;
import com.omnichannel.notification.repository.NotificationRepository;
import com.omnichannel.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     ObjectMapper objectMapper,
                                     TransactionTemplate transactionTemplate) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleIncomingEvent(Object rawPayload) {
        try {
            log.info(">>> [NotificationService] Ingested event from RabbitMQ: class={}, payload={}",
                    rawPayload != null ? rawPayload.getClass().getName() : "null", rawPayload);

            Map<String, Object> map = null;

            if (rawPayload instanceof Message) {
                byte[] body = ((Message) rawPayload).getBody();
                map = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            } else if (rawPayload instanceof byte[]) {
                map = objectMapper.readValue(new String((byte[]) rawPayload, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            } else if (rawPayload instanceof String) {
                map = objectMapper.readValue((String) rawPayload, new TypeReference<Map<String, Object>>() {});
            } else if (rawPayload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) rawPayload;
                map = casted;
            }

            if (map != null) {
                // Determine unique event key for consumer deduplication (Idempotent Consumer Pattern)
                String dedupKey = null;

                // Case 1: Payment Completed Event (contains transactionId)
                if (map.containsKey("transactionId")) {
                    Object orderId = map.get("orderId");
                    String txId = map.get("transactionId").toString();
                    dedupKey = "PAYMENT_COMPLETED:" + txId;

                    if (isDuplicateEvent(dedupKey)) {
                        return;
                    }

                    Object amount = map.get("amount");
                    String email = map.get("customerEmail") != null ? map.get("customerEmail").toString() : "customer@ecommerce.com";

                    NotificationLog logEntry = new NotificationLog(
                            email,
                            "Biên lai thanh toán thành công đơn hàng #" + orderId,
                            "Giao dịch thanh toán mã " + txId + " trị giá " + amount + " VNĐ đã được xác nhận thành công.",
                            "PAYMENT_COMPLETED"
                    );
                    saveNotificationAndMarkProcessed(logEntry, dedupKey);
                    log.info(">>> [NotificationService] Saved PAYMENT_COMPLETED notification for {}", email);
                }
                // Case 2: Payment Failed Event (contains reason)
                else if (map.containsKey("reason")) {
                    Object orderId = map.get("orderId");
                    String reason = map.get("reason").toString();
                    dedupKey = "PAYMENT_FAILED:" + orderId;

                    if (isDuplicateEvent(dedupKey)) {
                        return;
                    }

                    String email = map.get("customerEmail") != null ? map.get("customerEmail").toString() : "customer@ecommerce.com";

                    NotificationLog logEntry = new NotificationLog(
                            email,
                            "Cảnh báo thanh toán thất bại đơn hàng #" + orderId,
                            "Giao dịch thanh toán cho đơn hàng #" + orderId + " không thành công. Lý do: " + reason +
                            ". Hệ thống đã kích hoạt Saga bù trừ và hoàn lại tồn kho sản phẩm.",
                            "PAYMENT_FAILED"
                    );
                    saveNotificationAndMarkProcessed(logEntry, dedupKey);
                    log.info(">>> [NotificationService] Saved PAYMENT_FAILED notification for {}", email);
                }
                // Case 3: Order Created Event (contains orderNumber or items/totalAmount)
                else if (map.containsKey("orderNumber") || map.containsKey("totalAmount")) {
                    String orderNumber = map.get("orderNumber") != null ? map.get("orderNumber").toString() : "ORD-NEW";
                    dedupKey = "ORDER_CREATED:" + orderNumber;

                    if (isDuplicateEvent(dedupKey)) {
                        return;
                    }

                    String email = map.get("customerEmail") != null ? map.get("customerEmail").toString() : "customer@ecommerce.com";
                    Object total = map.get("totalAmount");

                    NotificationLog logEntry = new NotificationLog(
                            email,
                            "Xác nhận đặt hàng thành công #" + orderNumber,
                            "Kính chào quý khách! Hệ thống đã tiếp nhận đơn hàng #" + orderNumber +
                            " với giá trị " + total + " VNĐ. Chúng tôi đang xử lý và chuẩn bị giao hàng.",
                            "ORDER_CREATED"
                    );
                    saveNotificationAndMarkProcessed(logEntry, dedupKey);
                    log.info(">>> [NotificationService] Saved ORDER_CREATED notification for {}", email);
                }
            }
        } catch (Exception e) {
            log.error(">>> [NotificationService] Failed to process notification event: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi xử lý sự kiện thông báo: " + e.getMessage(), e);
        }
    }

    private boolean isDuplicateEvent(String dedupKey) {
        if (dedupKey == null) return false;
        if (processedEventRepository.existsById(dedupKey)) {
            log.warn(">>> [NotificationService Deduplication] Duplicate event intercepted with key: {}. Skipping notification to prevent duplicate delivery.", dedupKey);
            return true;
        }
        return false;
    }

    private void saveNotificationAndMarkProcessed(NotificationLog logEntry, String dedupKey) {
        try {
            transactionTemplate.execute(status -> {
                notificationRepository.save(logEntry);
                if (dedupKey != null) {
                    processedEventRepository.save(new ProcessedEvent(dedupKey));
                }
                return null;
            });
            log.info(">>> [NotificationService] Saved notification and marked ProcessedEvent for key: {}", dedupKey);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Concurrent consumer race condition on unique key: cleanly acknowledged and suppressed to prevent DLQ poison
            log.info(">>> [NotificationService Deduplication] Concurrent duplicate detected for event {}. Message safely acknowledged and ignored.", dedupKey);
        }
    }
}
