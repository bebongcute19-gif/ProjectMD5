package com.omnichannel.order.dto;

public class BenchmarkStatsDto {
    private Long productId;
    private Integer initialStock;
    private Integer currentStock;
    private Integer totalOrdersPlaced;
    private Integer successfulFlashSaleOrders;
    private Integer rejectedOrders;
    private boolean raceConditionDetected;

    public BenchmarkStatsDto() {}

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getInitialStock() { return initialStock; }
    public void setInitialStock(Integer initialStock) { this.initialStock = initialStock; }

    public Integer getCurrentStock() { return currentStock; }
    public void setCurrentStock(Integer currentStock) { this.currentStock = currentStock; }

    public Integer getTotalOrdersPlaced() { return totalOrdersPlaced; }
    public void setTotalOrdersPlaced(Integer totalOrdersPlaced) { this.totalOrdersPlaced = totalOrdersPlaced; }

    public Integer getSuccessfulFlashSaleOrders() { return successfulFlashSaleOrders; }
    public void setSuccessfulFlashSaleOrders(Integer successfulFlashSaleOrders) { this.successfulFlashSaleOrders = successfulFlashSaleOrders; }

    public Integer getRejectedOrders() { return rejectedOrders; }
    public void setRejectedOrders(Integer rejectedOrders) { this.rejectedOrders = rejectedOrders; }

    public boolean isRaceConditionDetected() { return raceConditionDetected; }
    public void setRaceConditionDetected(boolean raceConditionDetected) { this.raceConditionDetected = raceConditionDetected; }
}
