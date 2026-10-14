# API SPECIFICATIONS & CONTRACT DOCUMENTATION

**Omnichannel E-Commerce Microservices Platform**  
Tất cả các API được định tuyến tập trung qua **API Gateway (Port 8080)** theo chuẩn RESTful JSON.

---

## 1. Identity & User Service (`/api/v1/auth`)

### 1.1. Đăng ký tài khoản
- **Endpoint:** `POST /api/v1/auth/register`
- **Request Body:**
  ```json
  {
    "email": "customer@ecommerce.com",
    "password": "Customer@123",
    "fullName": "Nguyễn Văn A",
    "phone": "0987654321",
    "address": "TP. Hồ Chí Minh"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Đăng ký tài khoản thành công!",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "type": "Bearer",
      "user": {
        "id": 1,
        "email": "customer@ecommerce.com",
        "fullName": "Nguyễn Văn A",
        "role": "CUSTOMER"
      }
    }
  }
  ```

### 1.2. Đăng nhập
- **Endpoint:** `POST /api/v1/auth/login`
- **Request Body:**
  ```json
  {
    "email": "customer@ecommerce.com",
    "password": "Customer@123"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Đăng nhập thành công!",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "type": "Bearer",
      "user": { "id": 1, "email": "customer@ecommerce.com", "role": "CUSTOMER" }
    }
  }
  ```

### 1.3. Xác thực Token (Token Validation)
- **Endpoint:** `GET /api/v1/auth/validate`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "valid": true,
    "user": { "id": 1, "email": "customer@ecommerce.com", "role": "CUSTOMER" }
  }
  ```

---

## 2. Product Catalog Service (`/api/v1/products`)

### 2.1. Lấy danh sách sản phẩm
- **Endpoint:** `GET /api/v1/products`
- **Query Params:**
  - `category`: lọc theo slug danh mục (`smartphones`, `laptops`, `audio`, `accessories`)
  - `flashSale`: `true` để lấy các deal Flash Sale
  - `keyword`: tìm kiếm theo từ khóa
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "count": 5,
    "data": [
      {
        "id": 1,
        "name": "iPhone 16 Pro Max 256GB Desert Titanium (Flash Sale)",
        "sku": "IPHONE-16-PM-256",
        "price": 34990000,
        "stock": 10,
        "flashSale": true,
        "flashSalePrice": 19990000
      }
    ]
  }
  ```

### 2.2. Chi tiết sản phẩm
- **Endpoint:** `GET /api/v1/products/{id}`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "name": "iPhone 16 Pro Max 256GB Desert Titanium (Flash Sale)",
      "sku": "IPHONE-16-PM-256",
      "price": 34990000,
      "stock": 10,
      "imageUrl": "https://..."
    }
  }
  ```

---

## 3. Order & Inventory Service (`/api/v1/orders`)

### 3.1. Tạo đơn hàng (Bảo vệ bằng Redis Distributed Lock)
- **Endpoint:** `POST /api/v1/orders`
- **Headers:** `Authorization: Bearer <jwt_token>` (tuỳ chọn)
- **Request Body:**
  ```json
  {
    "customerId": 1,
    "customerEmail": "customer@ecommerce.com",
    "customerName": "Nguyễn Văn A",
    "shippingAddress": "77 Nguyễn Huệ, Q1, TP.HCM",
    "phone": "0987654321",
    "paymentMethod": "VNPAY",
    "items": [
      {
        "productId": 1,
        "productName": "iPhone 16 Pro Max 256GB",
        "sku": "IPHONE-16-PM-256",
        "price": 19990000,
        "quantity": 1
      }
    ]
  }
  ```
- **Response Thành Công (201 Created):**
  ```json
  {
    "success": true,
    "message": "Đặt hàng thành công! Kho đã được giữ an toàn.",
    "data": {
      "id": 12,
      "orderNumber": "ORD-1728645000-842",
      "customerId": 1,
      "totalAmount": 19990000,
      "status": "PENDING",
      "paymentMethod": "VNPAY"
    }
  }
  ```
- **Response Hết Hàng (409 Conflict):**
  ```json
  {
    "success": false,
    "message": "HẾT HÀNG! Sản phẩm iPhone 16 Pro Max chỉ còn 0 cái, không đủ đáp ứng yêu cầu (1)."
  }
  ```

### 3.2. Lấy danh sách đơn hàng
- **Endpoint:** `GET /api/v1/orders`
- **Query Params:** `customerId=1`

### 3.3. Reset Tồn Kho để Chạy Benchmark Flash Sale
- **Endpoint:** `POST /api/v1/orders/benchmark/reset-stock?productId=1&stock=10`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Đã reset tồn kho sản phẩm #1 về 10 để chạy Benchmark / Load Test!",
    "productId": 1,
    "stock": 10
  }
  ```

### 3.4. Xem Thống kê Kiểm thử Tải & Âm kho
- **Endpoint:** `GET /api/v1/orders/benchmark/stats?productId=1`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "productId": 1,
      "initialStock": 10,
      "currentStock": 0,
      "totalOrdersPlaced": 100,
      "successfulFlashSaleOrders": 10,
      "rejectedOrders": 90,
      "raceConditionDetected": false
    }
  }
  ```

---

## 4. Payment & Billing Service (`/api/v1/payments`)

### 4.1. Xử lý Thanh toán (Mô phỏng VNPay Sandbox & Saga)
- **Endpoint:** `POST /api/v1/payments/process`
- **Request Body (Thanh toán Thành công):**
  ```json
  {
    "orderId": 12,
    "orderNumber": "ORD-1728645000-842",
    "action": "SUCCESS"
  }
  ```
  *(Hệ thống phát `PaymentCompletedEvent` qua RabbitMQ -> Order Service chuyển trạng thái sang `CONFIRMED`)*
- **Request Body (Giả lập Thất bại để Kích hoạt Saga Rollback):**
  ```json
  {
    "orderId": 12,
    "orderNumber": "ORD-1728645000-842",
    "action": "FAIL"
  }
  ```
  *(Hệ thống phát `PaymentFailedEvent` qua RabbitMQ -> Order Service kích hoạt Compensating Transaction hoàn trả tồn kho và chuyển đơn sang `CANCELLED`)*

---

## 5. Notification Service (`/api/v1/notifications`)

### 5.1. Xem danh sách thông báo đã gửi
- **Endpoint:** `GET /api/v1/notifications`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "count": 2,
    "data": [
      {
        "id": 1,
        "recipientEmail": "customer@ecommerce.com",
        "title": "Xác nhận đặt hàng thành công #ORD-1728645000-842",
        "message": "Kính chào quý khách! Hệ thống đã tiếp nhận đơn hàng...",
        "eventType": "ORDER_CREATED",
        "status": "DELIVERED",
        "createdAt": "2026-10-11T13:40:00"
      }
    ]
  }
  ```
