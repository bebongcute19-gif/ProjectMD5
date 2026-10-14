package com.omnichannel.payment.service;

import com.omnichannel.payment.config.RabbitMQConfig;
import com.omnichannel.payment.dto.PaymentResponseDto;
import com.omnichannel.payment.dto.ProcessPaymentRequest;
import com.omnichannel.payment.entity.PaymentStatus;
import com.omnichannel.payment.entity.PaymentTransaction;
import com.omnichannel.payment.event.OrderCreatedEvent;
import com.omnichannel.payment.event.PaymentCompletedEvent;
import com.omnichannel.payment.event.PaymentFailedEvent;
import com.omnichannel.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * RabbitMQ Listener: Handles OrderCreatedEvent from Order Service
     * Automatically registers a PENDING payment transaction.
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE)
    public void onOrderCreated(Object rawMessage) {
        try {
            log.info(">>> [PaymentService] Received OrderCreatedEvent from RabbitMQ: class={}, payload={}",
                    rawMessage != null ? rawMessage.getClass().getName() : "null", rawMessage);

            Map<String, Object> map = null;

            if (rawMessage instanceof Message) {
                byte[] body = ((Message) rawMessage).getBody();
                map = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            } else if (rawMessage instanceof byte[]) {
                map = objectMapper.readValue(new String((byte[]) rawMessage, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            } else if (rawMessage instanceof String) {
                map = objectMapper.readValue((String) rawMessage, new TypeReference<Map<String, Object>>() {});
            } else if (rawMessage instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) rawMessage;
                map = casted;
            } else if (rawMessage instanceof OrderCreatedEvent) {
                OrderCreatedEvent evt = (OrderCreatedEvent) rawMessage;
                registerPendingTransaction(evt.getOrderId(), evt.getOrderNumber(), evt.getCustomerId(),
                        evt.getCustomerEmail(), evt.getTotalAmount(), evt.getPaymentMethod());
                return;
            }

            if (map != null && map.get("orderId") != null) {
                Long orderId = Long.valueOf(map.get("orderId").toString());
                String orderNumber = map.get("orderNumber") != null ? map.get("orderNumber").toString() : "ORD-" + orderId;
                Long customerId = map.get("customerId") != null ? Long.valueOf(map.get("customerId").toString()) : 1L;
                String customerEmail = map.get("customerEmail") != null ? map.get("customerEmail").toString() : "customer@ecommerce.com";
                BigDecimal amount = map.get("totalAmount") != null ? new BigDecimal(map.get("totalAmount").toString()) : BigDecimal.ZERO;
                String paymentMethod = map.get("paymentMethod") != null ? map.get("paymentMethod").toString() : "VNPAY";
                registerPendingTransaction(orderId, orderNumber, customerId, customerEmail, amount, paymentMethod);
            }
        } catch (Exception e) {
            log.error(">>> [PaymentService] Error handling OrderCreatedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi xử lý OrderCreatedEvent: " + e.getMessage(), e);
        }
    }

    private void registerPendingTransaction(Long orderId, String orderNumber, Long customerId, String customerEmail, BigDecimal amount, String paymentMethod) {
        Optional<PaymentTransaction> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isEmpty()) {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setTransactionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            tx.setOrderId(orderId);
            tx.setOrderNumber(orderNumber);
            tx.setCustomerId(customerId);
            tx.setCustomerEmail(customerEmail);
            tx.setAmount(amount);
            tx.setPaymentMethod(paymentMethod != null ? paymentMethod : "VNPAY");
            tx.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(tx);
            log.info(">>> [PaymentService] Created pending payment record #{} for order #{}", tx.getTransactionId(), orderNumber);
        }
    }

    /**
     * Process payment (VNPay simulation / user checkout action)
     * Supports action = "SUCCESS" or "FAIL" to demonstrate Saga Compensation!
     */
    @Transactional
    public PaymentResponseDto processPayment(ProcessPaymentRequest req) {
        PaymentTransaction tx = paymentRepository.findByOrderId(req.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Giao dịch cho đơn hàng #" + req.getOrderId() + " chưa sẵn sàng hoặc không tồn tại!"));

        if (tx.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Giao dịch cho đơn hàng #" + req.getOrderId() + " đã kết thúc (" + tx.getStatus() + "), không thể xử lý lại!");
        }

        // Validate client-submitted amount against registered order amount
        if (tx.getAmount() != null && req.getAmount() != null && tx.getAmount().compareTo(req.getAmount()) != 0) {
            log.warn(">>> [PaymentService] Fraud warning: Amount mismatch for order #{}. Expected: {}, Received: {}",
                    req.getOrderId(), tx.getAmount(), req.getAmount());
            throw new IllegalArgumentException("Số tiền thanh toán (" + req.getAmount() + " VNĐ) không khớp với giá trị đơn hàng (" + tx.getAmount() + " VNĐ)!");
        }

        boolean isSuccess = !"FAIL".equalsIgnoreCase(req.getAction());

        if (isSuccess) {
            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setProviderRef("VNPAY-REF-" + System.currentTimeMillis());
            tx.setCompletedAt(LocalDateTime.now());
            PaymentTransaction saved = paymentRepository.save(tx);

            // Publish PaymentCompletedEvent to RabbitMQ -> triggers Order confirmation & Notification
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    saved.getOrderId(),
                    saved.getOrderNumber(),
                    saved.getCustomerEmail(),
                    saved.getTransactionId(),
                    saved.getAmount(),
                    saved.getPaymentMethod()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.PAYMENT_COMPLETED_ROUTING_KEY,
                    event
            );
            log.info(">>> [PaymentService] Payment SUCCESS for Order ID {}. Emitted PaymentCompletedEvent!", saved.getOrderId());
            return new PaymentResponseDto(saved);

        } else {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason("Thanh toán bị từ chối / Khách hàng hủy giao dịch tại cổng VNPay");
            tx.setCompletedAt(LocalDateTime.now());
            PaymentTransaction saved = paymentRepository.save(tx);

            // Publish PaymentFailedEvent to RabbitMQ -> triggers Saga Compensating Transaction in Order Service!
            PaymentFailedEvent event = new PaymentFailedEvent(
                    saved.getOrderId(),
                    saved.getOrderNumber(),
                    saved.getCustomerEmail(),
                    saved.getFailureReason()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY,
                    event
            );
            log.warn(">>> [PaymentService] Payment FAILED for Order ID {}. Emitted PaymentFailedEvent for Saga Rollback!", saved.getOrderId());
            return new PaymentResponseDto(saved);
        }
    }

    public void validatePaymentOwnership(Long orderId, Long userId) {
        PaymentTransaction tx = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Giao dịch cho đơn hàng #" + orderId + " chưa sẵn sàng hoặc không tồn tại!"));
        if (tx.getCustomerId() != null && !tx.getCustomerId().equals(userId)) {
            throw new SecurityException(
                    "Bạn không có quyền thực hiện thanh toán hoặc thao tác trên đơn hàng của người khác!");
        }
    }

    public PaymentResponseDto getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponseDto::new)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch cho đơn hàng: " + orderId));
    }

    public List<PaymentResponseDto> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(PaymentResponseDto::new).collect(Collectors.toList());
    }

    public String generateVNPaySimulationUrl(Long orderId, BigDecimal amount) {
        return "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_OrderInfo=Thanh+toan+don+hang+" + orderId +
                "&vnp_Amount=" + (amount != null ? amount.longValue() * 100 : 10000000) +
                "&vnp_TxnRef=" + orderId + "-" + System.currentTimeMillis();
    }
}
