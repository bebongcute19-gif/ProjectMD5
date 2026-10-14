package com.omnichannel.payment.dto;

import java.math.BigDecimal;

public class ProcessPaymentRequest {
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private BigDecimal amount;
    private String paymentMethod = "VNPAY";
    private String action = "SUCCESS"; // "SUCCESS" or "FAIL" to test Saga rollback

    public ProcessPaymentRequest() {}

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
