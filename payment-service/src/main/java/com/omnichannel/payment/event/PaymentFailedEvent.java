package com.omnichannel.payment.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public class PaymentFailedEvent implements Serializable {
    private Long orderId;
    private String orderNumber;
    private String customerEmail;
    private String reason;
    private LocalDateTime failedAt;

    public PaymentFailedEvent() {
        this.failedAt = LocalDateTime.now();
    }

    public PaymentFailedEvent(Long orderId, String orderNumber, String customerEmail, String reason) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerEmail = customerEmail;
        this.reason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }
}
