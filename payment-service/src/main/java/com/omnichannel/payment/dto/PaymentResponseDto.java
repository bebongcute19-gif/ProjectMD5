package com.omnichannel.payment.dto;

import com.omnichannel.payment.entity.PaymentStatus;
import com.omnichannel.payment.entity.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponseDto {
    private Long id;
    private String transactionId;
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private BigDecimal amount;
    private String paymentMethod;
    private PaymentStatus status;
    private String providerRef;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public PaymentResponseDto() {}

    public PaymentResponseDto(PaymentTransaction tx) {
        this.id = tx.getId();
        this.transactionId = tx.getTransactionId();
        this.orderId = tx.getOrderId();
        this.orderNumber = tx.getOrderNumber();
        this.customerId = tx.getCustomerId();
        this.amount = tx.getAmount();
        this.paymentMethod = tx.getPaymentMethod();
        this.status = tx.getStatus();
        this.providerRef = tx.getProviderRef();
        this.failureReason = tx.getFailureReason();
        this.createdAt = tx.getCreatedAt();
        this.completedAt = tx.getCompletedAt();
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
