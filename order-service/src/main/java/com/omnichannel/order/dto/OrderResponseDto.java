package com.omnichannel.order.dto;

import com.omnichannel.order.entity.Order;
import com.omnichannel.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class OrderResponseDto {
    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private String customerName;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private List<ItemDto> items;

    public static class ItemDto {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;

        public ItemDto() {}
        public ItemDto(Long productId, String productName, BigDecimal price, Integer quantity, BigDecimal subtotal) {
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }

        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public BigDecimal getPrice() { return price; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getSubtotal() { return subtotal; }
    }

    public OrderResponseDto() {}

    public OrderResponseDto(Order o) {
        this.id = o.getId();
        this.orderNumber = o.getOrderNumber();
        this.customerId = o.getCustomerId();
        this.customerEmail = o.getCustomerEmail();
        this.customerName = o.getCustomerName();
        this.totalAmount = o.getTotalAmount();
        this.status = o.getStatus();
        this.paymentMethod = o.getPaymentMethod();
        this.createdAt = o.getCreatedAt();
        if (o.getItems() != null) {
            this.items = o.getItems().stream().map(i -> new ItemDto(
                i.getProductId(),
                i.getProductName(),
                i.getPrice(),
                i.getQuantity(),
                i.getSubtotal()
            )).collect(Collectors.toList());
        }
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ItemDto> getItems() { return items; }
}
