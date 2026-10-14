package com.omnichannel.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.order.config.RabbitMQConfig;
import com.omnichannel.order.entity.OutboxEvent;
import com.omnichannel.order.event.OrderCreatedEvent;
import com.omnichannel.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelay = 400)
    public void processOutboxEvents() {
        // Read pending event IDs without holding long DB transactions across RabbitMQ network calls
        List<Long> pendingIds = outboxRepository.findPendingEventIds("PENDING");
        if (pendingIds.isEmpty()) {
            return;
        }

        for (Long eventId : pendingIds) {
            try {
                publishSingleEventById(eventId);
            } catch (Exception e) {
                log.error(">>> [Outbox Pattern] Unexpected error processing event ID {}: {}", eventId, e.getMessage(), e);
            }
        }
    }

    public void publishSingleEventById(Long eventId) {
        // Programmatic TransactionTemplate guarantees PROPAGATION_REQUIRES_NEW works across self-invocations!
        // The row-level lock from SELECT FOR UPDATE SKIP LOCKED is actively held until the lambda completes.
        requiresNewTransactionTemplate.execute(status -> {
            java.util.Optional<OutboxEvent> opt = outboxRepository.findPendingByIdForUpdateSkipLocked(eventId);
            if (opt.isEmpty()) {
                // Safely skipped if another instance is already processing or committed this event
                return null;
            }
            publishSingleEvent(opt.get());
            return null;
        });
    }

    public void publishSingleEvent(OutboxEvent event) {
        try {
            if ("ORDER_CREATED".equalsIgnoreCase(event.getEventType())) {
                OrderCreatedEvent payloadObj = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        RabbitMQConfig.ORDER_CREATED_ROUTING_KEY,
                        payloadObj
                );
            } else {
                // Generic JSON dispatch
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        event.getEventType().toLowerCase(),
                        event.getPayload()
                );
            }

            event.setStatus("PUBLISHED");
            event.setProcessedAt(LocalDateTime.now());
            outboxRepository.save(event);
            log.info(">>> [Outbox Pattern] Dispatched event ID {} (Type: {}) for Aggregate {}",
                    event.getId(), event.getEventType(), event.getAggregateId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Permanent data serialization error
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            if (retries >= 5) {
                event.setStatus("FAILED");
                log.error(">>> [Outbox Pattern] Event ID {} malformed payload, marked FAILED: {}", event.getId(), e.getMessage());
            }
            outboxRepository.save(event);
        } catch (org.springframework.amqp.AmqpException e) {
            // Broker network disconnect / RabbitMQ down -> Keep PENDING, do not burn retry count!
            log.warn(">>> [Outbox Pattern] RabbitMQ Broker unavailable for Event ID {}: {}. Kept PENDING to retry on reconnect.",
                    event.getId(), e.getMessage());
        } catch (Exception e) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            if (retries >= 10) {
                event.setStatus("FAILED");
                log.error(">>> [Outbox Pattern] Event ID {} exceeded 10 attempts: {}", event.getId(), e.getMessage());
            } else {
                log.warn(">>> [Outbox Pattern] Event ID {} delivery error, will retry ({}/10): {}", event.getId(), retries, e.getMessage());
            }
            outboxRepository.save(event);
        }
    }
}
