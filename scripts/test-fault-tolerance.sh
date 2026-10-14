#!/bin/bash
# scripts/test-fault-tolerance.sh
# Kịch bản Linux/Bash minh họa Khả năng Chịu lỗi & Tính Sẵn sàng cao

echo "==========================================================="
echo "  DEMO TÍNH SẴN SÀNG & KHẢ NĂNG CHỊU LỖI (FAULT TOLERANCE) "
echo "==========================================================="

echo -e "\n[1/4] Dừng đột ngột container nexus-notification-service..."
docker stop nexus-notification-service

echo -e "\n[2/4] Tiến hành đặt đơn hàng khi Notification Service đang bị sập..."
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 999,
    "customerEmail": "fault_test@enterprise.com",
    "customerName": "Tester Fault Tolerance",
    "paymentMethod": "VNPAY",
    "items": [{"productId": 2, "productName": "Samsung S24 Ultra", "price": 31990000, "quantity": 1}]
  }' | jq .

echo -e "\n[3/4] Khởi động lại container nexus-notification-service..."
docker start nexus-notification-service
sleep 3

echo -e "\n[4/4] Kiểm tra các sự kiện đã được Notification Service tiêu thụ bù tự động..."
curl -s http://localhost:8080/api/v1/notifications | jq .
echo -e "\n[✓] Hoàn tất demo kiểm thử chịu lỗi thành công!"
