package com.omnichannel.order.event;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderItemDto implements Serializable {
    private Long productId;
    private String productName;
    private String sku;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;

    public OrderItemDto() {}

    public OrderItemDto(Long productId, String productName, String sku, BigDecimal price, Integer quantity, BigDecimal subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
