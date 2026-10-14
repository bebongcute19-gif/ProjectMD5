package com.omnichannel.product.dto;

import com.omnichannel.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDTO {
    private Long id;
    private String name;
    private String sku;
    private String slug;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String categorySlug;
    private String imageUrl;
    private boolean flashSale;
    private BigDecimal flashSalePrice;
    private String status;
    private LocalDateTime createdAt;

    public ProductDTO() {}

    public ProductDTO(Product p) {
        this.id = p.getId();
        this.name = p.getName();
        this.sku = p.getSku();
        this.slug = p.getSlug();
        this.description = p.getDescription();
        this.price = p.getPrice();
        this.originalPrice = p.getOriginalPrice();
        this.stock = p.getStock();
        this.categorySlug = p.getCategorySlug();
        this.imageUrl = p.getImageUrl();
        this.flashSale = p.isFlashSale();
        this.flashSalePrice = p.getFlashSalePrice();
        this.status = p.getStatus();
        this.createdAt = p.getCreatedAt();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isFlashSale() { return flashSale; }
    public void setFlashSale(boolean flashSale) { this.flashSale = flashSale; }

    public BigDecimal getFlashSalePrice() { return flashSalePrice; }
    public void setFlashSalePrice(BigDecimal flashSalePrice) { this.flashSalePrice = flashSalePrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
