# ⚡ NOVA COMMERCE - OMNICHANNEL E-COMMERCE MICROSERVICES PLATFORM

> **Đề tài tốt nghiệp / cuối kỳ:** Hệ thống Thương mại Điện tử Đa kênh chuẩn Doanh nghiệp theo kiến trúc Microservices với Redis Distributed Lock chống Race Condition và Saga Pattern RabbitMQ.

---

## 🌟 TỔNG QUAN HỆ THỐNG
Dự án được xây dựng hoàn chỉnh với các công nghệ chuẩn doanh nghiệp:
- **Backend:** Java 17, Spring Boot 3.2.5, Spring Cloud Gateway 2023, Spring Data JPA, Spring Security & JJWT.
- **Cơ sở dữ liệu:** PostgreSQL 16 (mô hình **Database-per-Service** với 5 database độc lập: `auth_db`, `product_db`, `order_db`, `payment_db`, `notification_db`).
- **Distributed Caching & Lock:** Redis 7 (chống Race Condition và Overselling khi Flash Sale với giải thuật **Redlock** và safe release qua **Lua Script**).
- **Message Broker:** RabbitMQ 3.13 (quản lý giao dịch phân tán qua **Saga Pattern Choreography**).
- **Frontend Portal:** Nginx 1.25, Single Page Application với giao diện Glassmorphism hiện đại (Storefront, Concurrency Benchmark Lab, Saga Explorer, Health Monitor).
- **Orchestration:** Docker & Docker Compose (đóng gói sẵn sàng chạy 1 lệnh trên máy cá nhân và VPS).

---

## 🏗️ DANH SÁCH MICROSERVICES & CỔNG KẾT NỐI

| Dịch vụ | Container Name | Cổng nội bộ | Cổng Public | Chức năng chính |
|:---|:---|:---:|:---:|:---|
| **API Gateway** | `nexus-api-gateway` | 8080 | **8080** | Cổng ra vào duy nhất, định tuyến, CORS, JWT Filter |
| **Frontend Portal** | `nexus-frontend` | 80 | **3000** | Web mua sắm, Benchmark tải đồng thời, Saga Monitor |
| **Auth Service** | `nexus-auth-service` | 8081 | 8081 | Quản lý người dùng, JWT token, RBAC |
| **Product Service** | `nexus-product-service` | 8082 | 8082 | Danh mục sản phẩm, flash sale deals |
| **Order Service** | `nexus-order-service` | 8083 | 8083 | **Core Service**: Redis Distributed Lock, Trừ kho, Saga |
| **Payment Service** | `nexus-payment-service` | 8084 | 8084 | Cổng thanh toán VNPay Sandbox, phát sự kiện Saga |
| **Notification Service**| `nexus-notification-service` | 8085 | 8085 | Tiêu thụ RabbitMQ event, log email/SMS |
| **PostgreSQL** | `nexus-postgres` | 5432 | 5432 | RDBMS lưu trữ ACID cho từng microservice |
| **Redis** | `nexus-redis` | 6379 | 6379 | Distributed Lock và In-Memory cache |
| **RabbitMQ** | `nexus-rabbitmq` | 5672 | 5672, **15672** | Message Broker & Dashboard quản trị RabbitMQ |

---

## 🚀 HƯỚNG DẪN KHỞI CHẠY (QUICK START)

### Yêu cầu tiên quyết:
- Đã cài đặt **Docker** và **Docker Compose**.

### Bước 1: Khởi động toàn bộ hệ thống
Mở terminal (PowerShell hoặc Bash) tại thư mục dự án và chạy:
```bash
docker compose up --build -d
```
Docker sẽ tự động:
1. Kéo image PostgreSQL, Redis, RabbitMQ.
2. Chạy script `scripts/init-databases.sql` tạo 5 database riêng biệt.
3. Build multi-stage từng Spring Boot service thành container Java 17 siêu nhẹ.
4. Khởi chạy toàn bộ hệ thống theo thứ tự phụ thuộc chính xác!

### Bước 2: Truy cập Giao diện Web
- **Frontend Portal:** [http://localhost:3000](http://localhost:3000)
- **API Gateway (Swagger/Health):** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **RabbitMQ Management Dashboard:** [http://localhost:15672](http://localhost:15672) (Tài khoản: `guest` / `guest`)

### Tài khoản mặc định trong hệ thống:
- **Khách hàng:** `customer@ecommerce.com` / `Customer@123`
- **Quản trị viên (Admin):** `admin@ecommerce.com` / `Admin@123`

---

## 🧪 KỊCH BẢN DEMO BẢO VỆ ĐỒ ÁN (ĐÁNH GIÁ ĐIỂM CAO)

### 1. Kịch bản 1: Demo Chống Race Condition khi Flash Sale (Trực quan trên Web & Script)
- **Cách 1 (Trực quan trên Web):**
  1. Vào [http://localhost:3000](http://localhost:3000) -> Chuyển sang Tab **"🔥 Benchmark Race Condition"**.
  2. Bấm nút **"Reset Kho về 10 Cái"**.
  3. Chọn số luồng đồng thời: **50 Luồng** hoặc **100 Luồng**.
  4. Bấm **"🚀 KHỞI CHẠY TEST TẢI ĐỒNG THỜI NGAY"**.
  5. **Kết quả chứng minh:** Chính xác 10 đơn hàng thành công, 40/90 đơn bị từ chối do hết hàng, tồn kho cuối cùng trong DB = 0, **Số sản phẩm bị Âm Kho = 0**!
- **Cách 2 (Chạy Script từ Console):**
  ```powershell
  # Chạy script PowerShell native trên Windows:
  powershell -ExecutionPolicy Bypass -File ./scripts/test-flash-sale.ps1

  # Hoặc script Python:
  python ./scripts/test-flash-sale.py
  ```

### 2. Kịch bản 2: Demo Giao Dịch Phân Tán Saga & Compensating Transaction
1. Vào Tab **"📦 Đơn Hàng"**.
2. Đặt một đơn hàng mới từ Storefront -> Đơn hàng ở trạng thái `PENDING`.
3. Bấm nút **"❌ Hủy / Bồi hoàn Saga"**:
   - `Payment Service` phát sự kiện `payment.failed` lên RabbitMQ.
   - `Order Service` nhận được sự kiện và thực hiện **Compensating Transaction**: Tự động cộng trả lại số lượng tồn kho sản phẩm và đổi trạng thái đơn sang `CANCELLED`.
   - `Notification Service` ghi nhận cảnh báo giao dịch thất bại.

### 3. Kịch bản 3: Demo Tính Sẵn Sàng & Chịu Lỗi (Fault Tolerance)
Chứng minh việc tắt đột ngột một service độc lập không làm sập luồng kinh doanh:
```powershell
# Chạy script kiểm thử khả năng chịu lỗi:
powershell -ExecutionPolicy Bypass -File ./scripts/test-fault-tolerance.ps1
```
*(Script sẽ tắt `nexus-notification-service`, tiến hành đặt hàng thành công 100%, sau đó bật lại service và chứng minh toàn bộ thông báo trong RabbitMQ được tiêu thụ hoàn chỉnh mà không mất mát!)*

---

## 📂 TÀI LIỆU BÁO CÁO CHI TIẾT
- 📙 [SỔ TAY GHI NHỚ & GIẢI TRÌNH BẢO VỆ ĐỒ ÁN (TIẾNG VIỆT)](docs/SO_TAY_GHI_NHO_BAO_VE_DO_AN.md)
- 📘 [BÁO CÁO CUỐI KỲ CHUYÊN SÂU (ĐẦY ĐỦ Ý 1 - 3 & HƯỚNG DẪN Ý 4)](docs/BAO_CAO_CUOI_KY_CHUYEN_SAU.md)
- 📗 [TÀI LIỆU ĐẶC TẢ API (OPENAPI / REST CONTRACT)](docs/API_SPECIFICATIONS.md)
