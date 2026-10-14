package com.omnichannel.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.order.config.RabbitMQConfig;
import com.omnichannel.order.event.PaymentCompletedEvent;
import com.omnichannel.order.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OrderSagaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaListener.class);
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderSagaListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void processPaymentCompleted(Object rawMessage) {
        try {
            log.info(">>> [OrderSagaListener:COMPLETED] Processing event: {}", rawMessage);
            PaymentCompletedEvent evt = parseEvent(rawMessage, PaymentCompletedEvent.class);
            if (evt != null && evt.getOrderId() != null) {
                orderService.handlePaymentCompleted(evt);
            } else {
                log.warn(">>> [OrderSagaListener:COMPLETED] Could not parse PaymentCompletedEvent from: {}", rawMessage);
            }
        } catch (Exception e) {
            log.error(">>> [OrderSagaListener:COMPLETED] Error processing payment completed: {}", e.getMessage(), e);
            throw new RuntimeException("Saga handling failed, retrying via RabbitMQ broker", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAYMENT_FAILED_QUEUE)
    public void processPaymentFailed(Object rawMessage) {
        try {
            log.info(">>> [OrderSagaListener:FAILED] Processing event: {}", rawMessage);
            PaymentFailedEvent evt = parseEvent(rawMessage, PaymentFailedEvent.class);
            if (evt != null && evt.getOrderId() != null) {
                orderService.handlePaymentFailed(evt);
            } else {
                log.warn(">>> [OrderSagaListener:FAILED] Could not parse PaymentFailedEvent from: {}", rawMessage);
            }
        } catch (Exception e) {
            log.error(">>> [OrderSagaListener:FAILED] Error processing payment failed: {}", e.getMessage(), e);
            throw new RuntimeException("Saga rollback handling failed, retrying via RabbitMQ broker", e);
        }
    }

    private <T> T parseEvent(Object rawMessage, Class<T> clazz) {
        try {
            if (clazz.isInstance(rawMessage)) {
                return clazz.cast(rawMessage);
            }
            if (rawMessage instanceof Message) {
                byte[] body = ((Message) rawMessage).getBody();
                return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), clazz);
            }
            if (rawMessage instanceof byte[]) {
                return objectMapper.readValue(new String((byte[]) rawMessage, StandardCharsets.UTF_8), clazz);
            }
            if (rawMessage instanceof String) {
                return objectMapper.readValue((String) rawMessage, clazz);
            }
            if (rawMessage instanceof Map) {
                return objectMapper.convertValue(rawMessage, clazz);
            }
        } catch (Exception e) {
            log.error(">>> [OrderSagaListener] Deserialization error for {}: {}", clazz.getSimpleName(), e.getMessage());
        }
        return null;
    }
}
