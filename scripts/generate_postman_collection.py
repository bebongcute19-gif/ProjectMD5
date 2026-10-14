import json
import os

collection = {
    "info": {
        "_postman_id": "a82b4519-74d2-4e90-8d5f-9e1234567890",
        "name": "NOVA Omnichannel E-Commerce Platform - Full Test Suite",
        "description": "Bộ sưu tập Postman Collection hoàn chỉnh và chuyên sâu cho toàn bộ hệ thống Microservices Thương Mại Điện Tử Đa Kênh (NOVA COMMERCE).\n\nBao gồm 7 Module kiểm thử toàn diện:\n1. Authentication & RBAC (Đăng nhập Admin/Customer, Đăng ký $timestamp, Token rác 401, Sai pass 401, Chống leo quyền, Chống giả mạo Header)\n2. Product Catalog (Public API, Lọc danh mục, Flash Sale deals, Tìm kiếm, Phân trang ?page&size, Chặn size>100, Admin CRUD, RBAC 403)\n3. Order & Inventory Management (Tồn kho thực tế, Khóa phân tán Redis Lock, Chống giả mạo giá & ID, Idempotency lặp lại/xung đột/sai payload, Hết hàng 409, Giỏ rỗng/Quantity âm 400, Tồn kho âm 400, BOLA/IDOR 403, Chuyển trạng thái 400/200, Hủy đơn & Hoàn kho)\n4. Concurrency Benchmark & Flash Sale Lab (Giải thích runner tuần tự vs k6 đồng thời, Reset kho, Thống kê DB & Chống âm kho)\n5. Payment & Saga Distributed Transactions (Saga SUCCESS -> CONFIRMED -> SHIPPING 200, Đơn riêng cho Saga FAIL -> CANCELLED & Hoàn kho, Chống thanh toán lại 400, Bảo vệ quyền sở hữu User A/User B 403, Admin audit log)\n6. Notification Service (Nhận sự kiện qua RabbitMQ, Customer A chỉ xem của mình, Chặn xem thông báo người khác, Admin xem tất cả)\n7. Health Checks Matrix (Kiểm tra Gateway Actuator và 5 Microservices downstream)",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080/api/v1", "type": "string"},
        {"key": "gatewayActuatorUrl", "value": "http://localhost:8080/actuator", "type": "string"},
        {"key": "admin_token", "value": "", "type": "string"},
        {"key": "customer_token", "value": "", "type": "string"},
        {"key": "customer_a_id", "value": "1", "type": "string"},
        {"key": "customer_b_token", "value": "", "type": "string"},
        {"key": "customer_b_id", "value": "2", "type": "string"},
        {"key": "latest_order_id", "value": "1", "type": "string"},
        {"key": "latest_order_number", "value": "ORD-SAMPLE", "type": "string"},
        {"key": "fail_order_id", "value": "", "type": "string"},
        {"key": "fail_order_number", "value": "", "type": "string"},
        {"key": "stock_before_fail", "value": "0", "type": "string"},
        {"key": "idem_first_order_id", "value": "", "type": "string"}
    ],
    "item": []
}

def make_req(name, method, url_raw, headers=None, body_json=None, tests=None, description="", prerequest=None):
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": [],
            "url": {
                "raw": url_raw
            }
        }
    }
    if description:
        item["request"]["description"] = description
        
    # parse url components
    clean_url = url_raw.replace("{{baseUrl}}", "http://localhost:8080/api/v1").replace("{{gatewayActuatorUrl}}", "http://localhost:8080/actuator")
    # For host/path
    parts = url_raw.split("?")[0].split("/")
    if url_raw.startswith("{{baseUrl}}"):
        item["request"]["url"]["host"] = ["{{baseUrl}}"]
        item["request"]["url"]["path"] = [p for p in url_raw.split("{{baseUrl}}/")[1].split("?")[0].split("/") if p]
    elif url_raw.startswith("{{gatewayActuatorUrl}}"):
        item["request"]["url"]["host"] = ["{{gatewayActuatorUrl}}"]
        item["request"]["url"]["path"] = [p for p in url_raw.split("{{gatewayActuatorUrl}}/")[1].split("?")[0].split("/") if p]
    else:
        item["request"]["url"]["path"] = [p for p in parts if p]

    if "?" in url_raw:
        query_str = url_raw.split("?")[1]
        item["request"]["url"]["query"] = []
        for q in query_str.split("&"):
            if "=" in q:
                k, v = q.split("=", 1)
                item["request"]["url"]["query"].append({"key": k, "value": v})

    if headers:
        for k, v in headers.items():
            item["request"]["header"].append({"key": k, "value": v})

    if body_json is not None:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body_json, indent=2, ensure_ascii=False) if isinstance(body_json, (dict, list)) else body_json
        }
        if not any(h["key"].lower() == "content-type" for h in item["request"]["header"]):
            item["request"]["header"].append({"key": "Content-Type", "value": "application/json"})

    if tests or prerequest:
        item["event"] = []
        if prerequest:
            item["event"].append({
                "listen": "prerequest",
                "script": {
                    "exec": prerequest,
                    "type": "text/javascript"
                }
            })
        if tests:
            item["event"].append({
                "listen": "test",
                "script": {
                    "exec": tests,
                    "type": "text/javascript"
                }
            })
    return item

# -------------------------------------------------------------
# MODULE 1: AUTHENTICATION & RBAC
# -------------------------------------------------------------
mod1 = {
    "name": "1. Authentication & RBAC",
    "item": [
        make_req(
            "1.1 Đăng Nhập Admin (Lưu Token Quản Trị)",
            "POST",
            "{{baseUrl}}/auth/login",
            body_json={"email": "admin@ecommerce.com", "password": "Admin@123"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Đăng nhập Admin thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.user.role).to.eql('ADMIN');",
                "});",
                "if (jsonData.success && jsonData.data && jsonData.data.token) {",
                "    pm.collectionVariables.set('admin_token', jsonData.data.token);",
                "    console.log('>>> [Admin Token saved]:', jsonData.data.token.substring(0, 20) + '...');",
                "}"
            ]
        ),
        make_req(
            "1.2 Đăng Nhập Customer A (Lưu Token Khách Mua A)",
            "POST",
            "{{baseUrl}}/auth/login",
            body_json={"email": "customer@ecommerce.com", "password": "Customer@123"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Đăng nhập Customer A thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.user.role).to.eql('CUSTOMER');",
                "});",
                "if (jsonData.success && jsonData.data && jsonData.data.token) {",
                "    pm.collectionVariables.set('customer_token', jsonData.data.token);",
                "    pm.collectionVariables.set('customer_a_id', jsonData.data.user.id);",
                "    console.log('>>> [Customer A Token saved, ID=' + jsonData.data.user.id + ']');",
                "}"
            ]
        ),
        make_req(
            "1.3 Đăng Nhập Customer B (Lưu Token Khách Mua B)",
            "POST",
            "{{baseUrl}}/auth/login",
            body_json={"email": "customer_b@ecommerce.com", "password": "Customer@123"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Đăng nhập Customer B thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.user.role).to.eql('CUSTOMER');",
                "});",
                "if (jsonData.success && jsonData.data && jsonData.data.token) {",
                "    pm.collectionVariables.set('customer_b_token', jsonData.data.token);",
                "    pm.collectionVariables.set('customer_b_id', jsonData.data.user.id);",
                "    console.log('>>> [Customer B Token saved, ID=' + jsonData.data.user.id + ']');",
                "}"
            ]
        ),
        make_req(
            "1.4 Đăng Ký Tài Khoản Mới (Email Động Chống Trùng Lặp)",
            "POST",
            "{{baseUrl}}/auth/register",
            body_json={
                "email": "customer_{{$timestamp}}@ecommerce.com",
                "password": "Customer@123",
                "fullName": "Người Dùng Test Mới",
                "phone": "0912345678",
                "address": "Hà Nội, Việt Nam"
            },
            tests=[
                "pm.test('Status code is 200/201 OK', function () { pm.expect(pm.response.code).to.be.oneOf([200, 201]); });",
                "var jsonData = pm.response.json();",
                "pm.test('Tài khoản mới được tạo thành công với vai trò CUSTOMER', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.user.role).to.eql('CUSTOMER');",
                "});"
            ]
        ),
        make_req(
            "1.5 Xác Thực JWT Token (Validate Active Token)",
            "GET",
            "{{baseUrl}}/auth/validate",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Token hợp lệ và trả về thông tin người dùng chính xác', function () {",
                "    pm.expect(jsonData.valid).to.eql(true);",
                "    pm.expect(jsonData.email).to.eql('customer@ecommerce.com');",
                "});"
            ]
        ),
        make_req(
            "1.6 Đăng Nhập Sai Mật Khẩu (Kỳ vọng 401 Unauthorized)",
            "POST",
            "{{baseUrl}}/auth/login",
            body_json={"email": "customer@ecommerce.com", "password": "WrongPassword@999"},
            tests=[
                "pm.test('Status code is 401 Unauthorized', function () { pm.response.to.have.status(401); });",
                "var jsonData = pm.response.json();",
                "pm.test('Hệ thống từ chối mật khẩu sai', function () {",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "1.7 Gửi Token Rác / Giả Mạo (Kỳ vọng 401 Unauthorized)",
            "GET",
            "{{baseUrl}}/auth/validate",
            headers={"Authorization": "Bearer invalid.jwt.token.here"},
            tests=[
                "pm.test('Status code is 401 Unauthorized', function () { pm.response.to.have.status(401); });",
                "var jsonData = pm.response.json();",
                "pm.test('Token rác bị từ chối chính xác', function () {",
                "    pm.expect(jsonData.valid).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "1.8 Chống Leo Quyền: Đăng Ký với role ADMIN nhưng Hệ Thống Ép về CUSTOMER",
            "POST",
            "{{baseUrl}}/auth/register",
            body_json={
                "email": "hacker_{{$timestamp}}@ecommerce.com",
                "password": "Hacker@123",
                "fullName": "Hacker Attacker",
                "role": "ADMIN"
            },
            tests=[
                "pm.test('Status code is 200/201 OK', function () { pm.expect(pm.response.code).to.be.oneOf([200, 201]); });",
                "var jsonData = pm.response.json();",
                "pm.test('Chống leo quyền thành công: role bị ép về CUSTOMER', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.user.role).to.eql('CUSTOMER');",
                "});"
            ]
        ),
        make_req(
            "1.9 Chống Giả Mạo Header X-User-Role: ADMIN (Gateway Overwrite Guard - Kỳ vọng 403 Forbidden)",
            "GET",
            "{{baseUrl}}/orders/admin/all",
            headers={
                "Authorization": "Bearer {{customer_token}}",
                "X-User-Role": "ADMIN"
            },
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });",
                "pm.test('Gateway ghi đè header giả mạo từ claims thật và từ chối truy cập', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        )
    ]
}
collection["item"].append(mod1)

# -------------------------------------------------------------
# MODULE 2: PRODUCT CATALOG
# -------------------------------------------------------------
mod2 = {
    "name": "2. Product Catalog (Public & Admin)",
    "item": [
        make_req(
            "2.1 Lấy Tất Cả Sản Phẩm (Public Catalog)",
            "GET",
            "{{baseUrl}}/products",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Danh sách sản phẩm tồn tại và có ít nhất 1 sản phẩm', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "});"
            ]
        ),
        make_req(
            "2.2 Lọc Sản Phẩm Flash Sale (Deals)",
            "GET",
            "{{baseUrl}}/products?flashSale=true",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Tất cả sản phẩm trả về đều có cờ flashSale=true và mảng không rỗng', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "    jsonData.data.forEach(function(item) { pm.expect(item.flashSale).to.eql(true); });",
                "});"
            ]
        ),
        make_req(
            "2.3 Lọc Sản Phẩm Theo Danh Mục (Smartphones)",
            "GET",
            "{{baseUrl}}/products?category=smartphones",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Sản phẩm thuộc danh mục smartphones và mảng không rỗng', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "    jsonData.data.forEach(function(item) { pm.expect(item.categorySlug).to.eql('smartphones'); });",
                "});"
            ]
        ),
        make_req(
            "2.4 Tìm Kiếm Sản Phẩm (Keyword Search)",
            "GET",
            "{{baseUrl}}/products?keyword=iPhone",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Kết quả tìm kiếm chứa từ khóa iPhone', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "    pm.expect(jsonData.data[0].name.toLowerCase()).to.include('iphone');",
                "});"
            ]
        ),
        make_req(
            "2.5 Chi Tiết Sản Phẩm #1 (iPhone 16 Pro Max)",
            "GET",
            "{{baseUrl}}/products/1",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Đúng ID sản phẩm #1', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.id).to.eql(1);",
                "});"
            ]
        ),
        make_req(
            "2.6 Lấy Danh Mục Sản Phẩm (Categories)",
            "GET",
            "{{baseUrl}}/products/categories",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Danh mục trả về mảng danh sách hợp lệ', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "});"
            ]
        ),
        make_req(
            "2.7 Phân Trang Sản Phẩm Hợp Lệ (?page=0&size=5)",
            "GET",
            "{{baseUrl}}/products?page=0&size=5",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Số lượng sản phẩm trang 1 không vượt quá size=5', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.length).to.be.at.most(5);",
                "    pm.expect(jsonData.page).to.eql(0);",
                "    pm.expect(jsonData.size).to.eql(5);",
                "});"
            ]
        ),
        make_req(
            "2.8 Giới Hạn Phân Trang Cực Đại (size=1000 bị chặn ở 100)",
            "GET",
            "{{baseUrl}}/products?page=0&size=1000",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Size bị giới hạn tối đa ở 100 để chống tràn bộ nhớ / full dump', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.size).to.eql(100);",
                "    pm.expect(jsonData.data.length).to.be.at.most(100);",
                "});"
            ]
        ),
        make_req(
            "2.9 Thêm Mới Sản Phẩm (Yêu cầu Quyền ADMIN)",
            "POST",
            "{{baseUrl}}/products",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={
                "name": "Apple Watch Ultra 2 Test",
                "description": "Đồng hồ thông minh thể thao chuyên nghiệp",
                "sku": "AW-ULTRA-2-TEST",
                "price": 21990000,
                "categorySlug": "smartwatches",
                "flashSale": True,
                "flashSalePrice": 19990000,
                "initialStock": 50
            },
            tests=[
                "pm.test('Status code is 201 Created', function () { pm.response.to.have.status(201); });",
                "var jsonData = pm.response.json();",
                "pm.test('Admin thêm mới sản phẩm thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.sku).to.eql('AW-ULTRA-2-TEST');",
                "});"
            ]
        ),
        make_req(
            "2.10 Thử Thêm Sản Phẩm Bằng Customer Token (Kỳ vọng 403 Forbidden)",
            "POST",
            "{{baseUrl}}/products",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "name": "Hacker Fake Product",
                "price": 1000,
                "sku": "HACKER-SKU"
            },
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });",
                "pm.test('Chặn thành công quyền thêm sản phẩm của Customer', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        )
    ]
}
collection["item"].append(mod2)

# -------------------------------------------------------------
# MODULE 3: ORDER & INVENTORY MANAGEMENT
# -------------------------------------------------------------
mod3 = {
    "name": "3. Order & Inventory Management",
    "item": [
        make_req(
            "3.1 Lấy Tồn Kho Thực Tế Sản Phẩm (Public GET /orders/inventory/1)",
            "GET",
            "{{baseUrl}}/orders/inventory/1",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Tồn kho trả về có trường availableStock và reservedStock', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.availableStock).to.be.a('number');",
                "    pm.expect(jsonData.reservedStock).to.be.a('number');",
                "});"
            ]
        ),
        make_req(
            "3.2 Tạo Đơn Hàng Thành Công (Customer Token - Lưu latest_order_id)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "123 Đường Cầu Giấy, Hà Nội",
                "phone": "0988776655",
                "paymentMethod": "VNPAY",
                "notes": "Giao giờ hành chính",
                "items": [
                    {
                        "productId": 1,
                        "quantity": 1
                    }
                ]
            },
            tests=[
                "pm.test('Status code is 201 Created', function () { pm.response.to.have.status(201); });",
                "var jsonData = pm.response.json();",
                "pm.test('Đặt hàng thành công và kho đã giữ an toàn', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.status).to.eql('PENDING');",
                "    pm.expect(jsonData.data.orderNumber).to.not.be.empty;",
                "});",
                "if (jsonData.success && jsonData.data && jsonData.data.id) {",
                "    pm.collectionVariables.set('latest_order_id', jsonData.data.id);",
                "    pm.collectionVariables.set('latest_order_number', jsonData.data.orderNumber);",
                "    console.log('>>> [Saved latest_order_id=' + jsonData.data.id + ', orderNumber=' + jsonData.data.orderNumber + ']');",
                "}"
            ]
        ),
        make_req(
            "3.3 Thử Tạo Đơn Không Có Token (Kỳ vọng 401 Unauthorized)",
            "POST",
            "{{baseUrl}}/orders",
            body_json={
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 401 Unauthorized', function () { pm.response.to.have.status(401); });"
            ]
        ),
        make_req(
            "3.4 Chống Giả Mạo Giá & ID (Price & Identity Tampering Guard)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "customerId": 9999,
                "customerEmail": "hacker@blackhat.com",
                "shippingAddress": "Dark Web Lane",
                "paymentMethod": "VNPAY",
                "items": [
                    {
                        "productId": 1,
                        "quantity": 1,
                        "price": 1000
                    }
                ]
            },
            tests=[
                "pm.test('Status code is 201 Created', function () { pm.response.to.have.status(201); });",
                "var jsonData = pm.response.json();",
                "pm.test('Bảo vệ danh tính: customerId và email được lấy từ JWT Token, chặn hacker', function () {",
                "    pm.expect(jsonData.data.customerId).to.not.eql(9999);",
                "    pm.expect(jsonData.data.customerEmail).to.not.eql('hacker@blackhat.com');",
                "    pm.expect(jsonData.data.customerEmail).to.eql('customer@ecommerce.com');",
                "});",
                "pm.test('Bảo vệ giá: Giá sản phẩm bị ghi đè bởi giá thực tế catalog (không bị đổi thành 1000đ)', function () {",
                "    pm.expect(jsonData.data.items[0].price).to.be.above(10000000);",
                "});"
            ]
        ),
        make_req(
            "3.5 Idempotency: Gửi 2 Lần Cùng Idempotency-Key (Kỳ vọng Trả Về Cùng 1 Đơn Hàng)",
            "POST",
            "{{baseUrl}}/orders",
            headers={
                "Authorization": "Bearer {{customer_token}}",
                "Idempotency-Key": "IDEM-TEST-1001-UNIQUE"
            },
            body_json={
                "shippingAddress": "Idempotency Lab Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 201 Created hoặc 200 OK', function () { pm.expect(pm.response.code).to.be.oneOf([200, 201]); });",
                "var jsonData = pm.response.json();",
                "pm.test('Nhận về đơn hàng hợp lệ', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.orderNumber).to.not.be.empty;",
                "});",
                "if (jsonData.data && jsonData.data.id) {",
                "    pm.collectionVariables.set('idem_first_order_id', jsonData.data.id);",
                "}"
            ]
        ),
        make_req(
            "3.5b Idempotency Lần 2: Gửi Lại Y Hệt Với Cùng Key (Kỳ vọng Nhận Lại Cùng Đơn Hàng Cũ)",
            "POST",
            "{{baseUrl}}/orders",
            headers={
                "Authorization": "Bearer {{customer_token}}",
                "Idempotency-Key": "IDEM-TEST-1001-UNIQUE"
            },
            body_json={
                "shippingAddress": "Idempotency Lab Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 200 hoặc 201', function () { pm.expect(pm.response.code).to.be.oneOf([200, 201]); });",
                "var jsonData = pm.response.json();",
                "var firstId = pm.collectionVariables.get('idem_first_order_id');",
                "pm.test('Idempotency thành công: Đơn hàng trả về có đúng ID của lần gọi đầu, không tạo đơn mới', function () {",
                "    pm.expect(String(jsonData.data.id)).to.eql(String(firstId));",
                "});"
            ]
        ),
        make_req(
            "3.6 Idempotency: User B Cố Tình Dùng Key Của User A (Kỳ vọng 409 Conflict)",
            "POST",
            "{{baseUrl}}/orders",
            headers={
                "Authorization": "Bearer {{customer_b_token}}",
                "Idempotency-Key": "IDEM-TEST-1001-UNIQUE"
            },
            body_json={
                "shippingAddress": "Attacker Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 409 Conflict', function () { pm.response.to.have.status(409); });",
                "pm.test('Chống rò rỉ đơn hàng: Từ chối khi User B cố tái sử dụng Idempotency-Key của User A', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.7 Idempotency: Cùng Key Nhưng Giỏ Hàng Khác Nhau (Kỳ vọng 422 Unprocessable Entity)",
            "POST",
            "{{baseUrl}}/orders",
            headers={
                "Authorization": "Bearer {{customer_token}}",
                "Idempotency-Key": "IDEM-TEST-1001-UNIQUE"
            },
            body_json={
                "shippingAddress": "Changed Cart Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 2, "quantity": 2}]
            },
            tests=[
                "pm.test('Status code is 422 Unprocessable Entity', function () { pm.response.to.have.status(422); });",
                "pm.test('Chuẩn IETF Idempotency: Từ chối khi nội dung giỏ hàng bị thay đổi trên cùng key', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.8 Hết Hàng: Đặt Hàng Vượt Quá Tồn Kho Thực Tế (Kỳ vọng 409 Conflict)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "Overstock Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 999999}]
            },
            tests=[
                "pm.test('Status code is 409 Conflict', function () { pm.response.to.have.status(409); });",
                "pm.test('Từ chối an toàn khi số lượng đặt vượt quá tồn kho', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.9 Dữ Liệu Sai: Giỏ Hàng Rỗng (Kỳ vọng 400 Bad Request)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "Empty Cart Address",
                "paymentMethod": "VNPAY",
                "items": []
            },
            tests=[
                "pm.test('Status code is 400 Bad Request', function () { pm.response.to.have.status(400); });",
                "pm.test('Bắt lỗi giỏ hàng rỗng thành công', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.10 Dữ Liệu Sai: Số Lượng Sản Phẩm Bằng 0 Hoặc Âm (Kỳ vọng 400 Bad Request)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "Negative Quantity Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": -2}]
            },
            tests=[
                "pm.test('Status code is 400 Bad Request', function () { pm.response.to.have.status(400); });",
                "pm.test('Bắt lỗi số lượng âm thành công', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.11 Quản Trị Kho Sai: Admin Cập Nhật Tồn Kho Âm (Kỳ vọng 400 Bad Request)",
            "PUT",
            "{{baseUrl}}/orders/admin/inventory/1",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={"stock": -5},
            tests=[
                "pm.test('Status code is 400 Bad Request', function () { pm.response.to.have.status(400); });",
                "pm.test('Từ chối cập nhật tồn kho âm', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.12 Lấy Danh Sách Đơn Hàng Của Tôi (Customer GET /orders)",
            "GET",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Danh sách đơn hàng của khách hàng hợp lệ', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "});"
            ]
        ),
        make_req(
            "3.13 Phân Trang Đơn Hàng Của Tôi (?page=0&size=5)",
            "GET",
            "{{baseUrl}}/orders?page=0&size=5",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Phân trang đơn hàng trả về thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.length).to.be.at.most(5);",
                "});"
            ]
        ),
        make_req(
            "3.14 Bảo Vệ Quyền Sở Hữu (BOLA/IDOR): User B Cố Xem Đơn Của User A (Kỳ vọng 403 Forbidden)",
            "GET",
            "{{baseUrl}}/orders/{{latest_order_id}}",
            headers={"Authorization": "Bearer {{customer_b_token}}"},
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });",
                "pm.test('Chặn thành công lỗi BOLA/IDOR (User B không xem được đơn của User A)', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.15 Lấy Toàn Bộ Đơn Hàng Hệ Thống (Admin GET /orders/admin/all)",
            "GET",
            "{{baseUrl}}/orders/admin/all",
            headers={"Authorization": "Bearer {{admin_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Admin xem được toàn bộ danh sách đơn', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "});"
            ]
        ),
        make_req(
            "3.16 Thử Lấy Toàn Bộ Đơn Bằng Customer (Kỳ vọng 403 Forbidden)",
            "GET",
            "{{baseUrl}}/orders/admin/all",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });"
            ]
        ),
        make_req(
            "3.17 Cập Nhật Tồn Kho (Admin PUT /orders/admin/inventory/1)",
            "PUT",
            "{{baseUrl}}/orders/admin/inventory/1",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={"stock": 50},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Cập nhật kho sản phẩm #1 thành 50 thành công', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.availableStock).to.eql(50);",
                "});"
            ]
        ),
        make_req(
            "3.18 Chuyển Trạng Thái Đơn Hàng Không Hợp Lệ (Admin PUT PENDING -> SHIPPING - Kỳ vọng 400 Bad Request)",
            "PUT",
            "{{baseUrl}}/orders/{{latest_order_id}}/status",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={"status": "SHIPPING"},
            tests=[
                "pm.test('Status code is 400 Bad Request', function () { pm.response.to.have.status(400); });",
                "pm.test('Hệ thống chặn chuyển trạng thái không hợp lệ (PENDING không thể lên SHIPPING trực tiếp)', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "3.19 Hủy Đơn Hàng PENDING & Hoàn Lại Kho Hàng (Admin PUT CANCELLED)",
            "PUT",
            "{{baseUrl}}/orders/{{latest_order_id}}/status",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={"status": "CANCELLED"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Hủy đơn hàng thành công và trạng thái là CANCELLED', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.status).to.eql('CANCELLED');",
                "});"
            ]
        )
    ]
}
collection["item"].append(mod3)

# -------------------------------------------------------------
# MODULE 4: CONCURRENCY BENCHMARK & FLASH SALE LAB
# -------------------------------------------------------------
mod4 = {
    "name": "4. Concurrency Benchmark & Flash Sale Lab",
    "description": "LƯU Ý QUAN TRỌNG: Postman Runner chạy các request kiểm thử tuần tự theo luồng đơn (Single-threaded). Do đó Postman dùng để kiểm tra API Reset kho, Thống kê DB và các endpoint điều khiển. Để chạy kiểm thử bắn tải đồng thời thực tế (Concurrent Stress Testing 100 - 1000 luồng) và chứng minh chống Race Condition / Âm kho, hãy sử dụng k6 (scripts/load-test-k6.js) hoặc PowerShell (scripts/test-hardened-logic.ps1).",
    "item": [
        make_req(
            "4.1 Reset Kho Sản Phẩm #1 về 100 Cái (Admin Token)",
            "POST",
            "{{baseUrl}}/orders/benchmark/reset-stock?productId=1&stock=100",
            headers={"Authorization": "Bearer {{admin_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Reset kho thành công về 100 cái', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.stock).to.eql(100);",
                "});"
            ]
        ),
        make_req(
            "4.2 Xem Thống Kê Benchmark & Kiểm Tra Âm Kho (Race Condition Guard)",
            "GET",
            "{{baseUrl}}/orders/benchmark/stats?productId=1",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Số lượng âm kho tuyệt đối bằng 0 (KHÔNG CÓ OVERSOLD)', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.oversoldCount).to.eql(0);",
                "    pm.expect(jsonData.data.currentStock).to.be.at.least(0);",
                "});"
            ]
        ),
        make_req(
            "4.3 Thử Reset Kho Bằng Customer Token (Kỳ vọng 403 Forbidden)",
            "POST",
            "{{baseUrl}}/orders/benchmark/reset-stock?productId=1&stock=100",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });"
            ]
        )
    ]
}
collection["item"].append(mod4)

# -------------------------------------------------------------
# MODULE 5: PAYMENT & SAGA DISTRIBUTED TRANSACTIONS
# -------------------------------------------------------------
mod5 = {
    "name": "5. Payment & Saga Distributed Transactions",
    "item": [
        make_req(
            "5.0 Chuẩn Bị: Tạo Đơn Hàng Cho Nhánh Saga SUCCESS",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "Saga Success Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 201 Created', function () { pm.response.to.have.status(201); });",
                "var jsonData = pm.response.json();",
                "if (jsonData.data && jsonData.data.id) {",
                "    pm.collectionVariables.set('latest_order_id', jsonData.data.id);",
                "    pm.collectionVariables.set('latest_order_number', jsonData.data.orderNumber);",
                "    console.log('>>> [Saga SUCCESS order prepared: ID=' + jsonData.data.id + ']');",
                "}"
            ]
        ),
        make_req(
            "5.1 Lấy Chi Tiết Giao Dịch Thanh Toán (RabbitMQ Outbox Event)",
            "GET",
            "{{baseUrl}}/payments/order/{{latest_order_id}}",
            headers={"Authorization": "Bearer {{customer_token}}"},
            description="LƯU Ý: Giao dịch thanh toán được tạo bất đồng bộ qua Outbox Publisher và RabbitMQ sau khi OrderCreatedEvent được phát. Script pre-request tự động chờ 1.5s để đảm bảo giao dịch đã sẵn sàng.",
            prerequest=[
                "// Chờ 1.5 giây để Outbox Publisher & RabbitMQ hoàn tất tạo giao dịch thanh toán bất đồng bộ",
                "setTimeout(function(){}, 1500);",
                "var start = new Date().getTime();",
                "while (new Date().getTime() < start + 1200);"
            ],
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Giao dịch thanh toán ở trạng thái PENDING', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.status).to.eql('PENDING');",
                "});"
            ]
        ),
        make_req(
            "5.2 Thanh Toán Thành Công (Saga -> Chuyển Đơn CONFIRMED)",
            "POST",
            "{{baseUrl}}/payments/process",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "orderId": "{{latest_order_id}}",
                "action": "SUCCESS"
            },
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Thanh toán thành công qua VNPay và phát sự kiện PaymentCompletedEvent', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.status).to.eql('SUCCESS');",
                "});"
            ]
        ),
        make_req(
            "5.3 Xác Thực Saga SUCCESS: Đơn Hàng Thật Sự Chuyển Sang CONFIRMED",
            "GET",
            "{{baseUrl}}/orders/{{latest_order_id}}",
            headers={"Authorization": "Bearer {{customer_token}}"},
            prerequest=[
                "// Chờ 1.5 giây để PaymentCompletedEvent qua RabbitMQ được Order Service xử lý chuyển trạng thái sang CONFIRMED",
                "setTimeout(function(){}, 1500);",
                "var start = new Date().getTime();",
                "while (new Date().getTime() < start + 1200);"
            ],
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Saga xác nhận thành công: Trạng thái đơn hàng chuyển sang CONFIRMED', function () {",
                "    pm.expect(jsonData.data.status).to.eql('CONFIRMED');",
                "});"
            ]
        ),
        make_req(
            "5.4 Chuyển Trạng Thái Hợp Lệ Sau Khi Đã CONFIRMED (Admin PUT CONFIRMED -> SHIPPING)",
            "PUT",
            "{{baseUrl}}/orders/{{latest_order_id}}/status",
            headers={"Authorization": "Bearer {{admin_token}}"},
            body_json={"status": "SHIPPING"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Chuyển trạng thái hợp lệ thành công sang SHIPPING', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data.status).to.eql('SHIPPING');",
                "});"
            ]
        ),
        make_req(
            "5.5 Chống Thanh Toán Lại Giao Dịch Đã Hoàn Tất (Idempotency Guard - Kỳ vọng 400 Bad Request)",
            "POST",
            "{{baseUrl}}/payments/process",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "orderId": "{{latest_order_id}}",
                "action": "SUCCESS"
            },
            tests=[
                "pm.test('Status code is 400 Bad Request', function () { pm.response.to.have.status(400); });",
                "pm.test('Chống thanh toán trùng lặp: Từ chối giao dịch đã kết thúc', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "5.6 Chuẩn Bị Đơn Hàng Mới Riêng Cho Kịch Bản Thanh Toán Thất Bại (Saga FAIL)",
            "POST",
            "{{baseUrl}}/orders",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "shippingAddress": "Saga Failure Lab Address",
                "paymentMethod": "VNPAY",
                "items": [{"productId": 1, "quantity": 1}]
            },
            tests=[
                "pm.test('Status code is 201 Created', function () { pm.response.to.have.status(201); });",
                "var jsonData = pm.response.json();",
                "if (jsonData.data && jsonData.data.id) {",
                "    pm.collectionVariables.set('fail_order_id', jsonData.data.id);",
                "    pm.collectionVariables.set('fail_order_number', jsonData.data.orderNumber);",
                "    console.log('>>> [Saga FAIL order prepared: ID=' + jsonData.data.id + ']');",
                "}"
            ]
        ),
        make_req(
            "5.6b Ghi Nhận Tồn Kho Hiện Tại Trước Khi Bồi Hoàn",
            "GET",
            "{{baseUrl}}/orders/inventory/1",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.collectionVariables.set('stock_before_fail', jsonData.availableStock);",
                "console.log('>>> [Tồn kho trước khi thanh toán FAIL]:', jsonData.availableStock);"
            ]
        ),
        make_req(
            "5.7 Giả Lập Thanh Toán Thất Bại (Saga Choreography -> Bồi Hoàn CANCELLED)",
            "POST",
            "{{baseUrl}}/payments/process",
            headers={"Authorization": "Bearer {{customer_token}}"},
            body_json={
                "orderId": "{{fail_order_id}}",
                "action": "FAIL"
            },
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Kích hoạt Saga bồi hoàn hoàn kho khi thanh toán thất bại', function () {",
                "    pm.expect(jsonData.data.status).to.eql('FAILED');",
                "});"
            ]
        ),
        make_req(
            "5.8 Xác Thực Saga Compensation: Đơn Hàng Tự Động Bị CANCELLED",
            "GET",
            "{{baseUrl}}/orders/{{fail_order_id}}",
            headers={"Authorization": "Bearer {{customer_token}}"},
            prerequest=[
                "// Chờ 1.5 giây để PaymentFailedEvent qua RabbitMQ được Order Service xử lý hủy đơn sang CANCELLED và hoàn kho",
                "setTimeout(function(){}, 1500);",
                "var start = new Date().getTime();",
                "while (new Date().getTime() < start + 1200);"
            ],
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Saga bồi hoàn thành công: Đơn hàng tự động hủy sang CANCELLED', function () {",
                "    pm.expect(jsonData.data.status).to.eql('CANCELLED');",
                "});"
            ]
        ),
        make_req(
            "5.9 Xác Thực Saga Compensation: Kho Hàng Được Hoàn Trả Đầy Đủ",
            "GET",
            "{{baseUrl}}/orders/inventory/1",
            prerequest=[
                "// Chờ kho được cập nhật hoàn trả trong DB sau Saga Compensation",
                "setTimeout(function(){}, 1000);",
                "var start = new Date().getTime();",
                "while (new Date().getTime() < start + 1000);"
            ],
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "var beforeStock = parseInt(pm.collectionVariables.get('stock_before_fail'), 10);",
                "pm.test('Kho hàng đã được hoàn trả chính xác 1 đơn vị', function () {",
                "    pm.expect(jsonData.availableStock).to.eql(beforeStock + 1);",
                "});"
            ]
        ),
        make_req(
            "5.10 User B Cố Thanh Toán Đơn Của User A (Ownership Guard - Kỳ vọng 403 Forbidden)",
            "POST",
            "{{baseUrl}}/payments/process",
            headers={"Authorization": "Bearer {{customer_b_token}}"},
            body_json={
                "orderId": "{{latest_order_id}}",
                "action": "SUCCESS"
            },
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });",
                "pm.test('Chống gian lận: User B không được quyền thanh toán đơn hàng của User A', function () {",
                "    var jsonData = pm.response.json();",
                "    pm.expect(jsonData.success).to.eql(false);",
                "});"
            ]
        ),
        make_req(
            "5.11 Lấy Toàn Bộ Giao Dịch Thanh Toán (Admin GET /payments)",
            "GET",
            "{{baseUrl}}/payments",
            headers={"Authorization": "Bearer {{admin_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Admin xem được nhật ký giao dịch thanh toán toàn hệ thống', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "});"
            ]
        ),
        make_req(
            "5.12 Thử Lấy Toàn Bộ Giao Dịch Bằng Customer (Kỳ vọng 403 Forbidden)",
            "GET",
            "{{baseUrl}}/payments",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 403 Forbidden', function () { pm.response.to.have.status(403); });"
            ]
        )
    ]
}
collection["item"].append(mod5)

# -------------------------------------------------------------
# MODULE 6: NOTIFICATION SERVICE
# -------------------------------------------------------------
mod6 = {
    "name": "6. Notification Service (RabbitMQ Events)",
    "item": [
        make_req(
            "6.1 Lấy Danh Sách Thông Báo Của Customer A (Chỉ Xem Thông Báo Của Mình)",
            "GET",
            "{{baseUrl}}/notifications",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Khách hàng nhận được thông báo của chính mình qua RabbitMQ', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "    pm.expect(jsonData.data.length).to.be.above(0);",
                "    jsonData.data.forEach(function(item) {",
                "        pm.expect(item.recipientEmail).to.eql('customer@ecommerce.com');",
                "    });",
                "});"
            ]
        ),
        make_req(
            "6.2 Customer A Không Xem Được Thông Báo Người Khác (Bảo Mật Quyền Riêng Tư)",
            "GET",
            "{{baseUrl}}/notifications?email=customer_b@ecommerce.com",
            headers={"Authorization": "Bearer {{customer_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Hệ thống ép lọc theo token và không trả về thông báo của customer_b', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    jsonData.data.forEach(function(item) {",
                "        pm.expect(item.recipientEmail).to.not.eql('customer_b@ecommerce.com');",
                "    });",
                "});"
            ]
        ),
        make_req(
            "6.3 Admin Xem Toàn Bộ Thông Báo Hệ Thống (Admin Token)",
            "GET",
            "{{baseUrl}}/notifications",
            headers={"Authorization": "Bearer {{admin_token}}"},
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Admin xem được toàn bộ thông báo hệ thống', function () {",
                "    pm.expect(jsonData.success).to.eql(true);",
                "    pm.expect(jsonData.data).to.be.an('array');",
                "});"
            ]
        ),
        make_req(
            "6.4 Thử Lấy Thông Báo Không Có Token (Kỳ vọng 401 Unauthorized)",
            "GET",
            "{{baseUrl}}/notifications",
            tests=[
                "pm.test('Status code is 401 Unauthorized', function () { pm.response.to.have.status(401); });"
            ]
        )
    ]
}
collection["item"].append(mod6)

# -------------------------------------------------------------
# MODULE 7: HEALTH CHECKS MATRIX
# -------------------------------------------------------------
mod7 = {
    "name": "7. Health Checks Matrix (6 Microservices)",
    "item": [
        make_req(
            "7.1 API Gateway Actuator Health",
            "GET",
            "{{gatewayActuatorUrl}}/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('API Gateway Spring Cloud Actuator is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        ),
        make_req(
            "7.2 Auth Service Health",
            "GET",
            "{{baseUrl}}/auth/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Auth Service is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        ),
        make_req(
            "7.3 Product Service Health",
            "GET",
            "{{baseUrl}}/products/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Product Service is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        ),
        make_req(
            "7.4 Order Service Health",
            "GET",
            "{{baseUrl}}/orders/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Order Service is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        ),
        make_req(
            "7.5 Payment Service Health",
            "GET",
            "{{baseUrl}}/payments/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Payment Service is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        ),
        make_req(
            "7.6 Notification Service Health",
            "GET",
            "{{baseUrl}}/notifications/health",
            tests=[
                "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                "var jsonData = pm.response.json();",
                "pm.test('Notification Service is UP', function () {",
                "    pm.expect(jsonData.status).to.eql('UP');",
                "});"
            ]
        )
    ]
}
collection["item"].append(mod7)

# Save Collection
col_path = r"docs/Omnichannel_Ecommerce_Platform.postman_collection.json"
with open(col_path, "w", encoding="utf-8") as f:
    json.dump(collection, f, indent=2, ensure_ascii=False)
print("Saved collection successfully:", col_path)

# Save Environment
environment = {
    "id": "e84f5901-1234-4567-890a-bcdef0123456",
    "name": "NOVA E-Commerce - Local & VPS Environment",
    "values": [
        {
            "key": "baseUrl",
            "value": "http://localhost:8080/api/v1",
            "type": "default",
            "enabled": True
        },
        {
            "key": "gatewayActuatorUrl",
            "value": "http://localhost:8080/actuator",
            "type": "default",
            "enabled": True
        },
        {
            "key": "admin_token",
            "value": "",
            "type": "secret",
            "enabled": True
        },
        {
            "key": "customer_token",
            "value": "",
            "type": "secret",
            "enabled": True
        },
        {
            "key": "customer_a_id",
            "value": "1",
            "type": "default",
            "enabled": True
        },
        {
            "key": "customer_b_token",
            "value": "",
            "type": "secret",
            "enabled": True
        },
        {
            "key": "customer_b_id",
            "value": "2",
            "type": "default",
            "enabled": True
        },
        {
            "key": "latest_order_id",
            "value": "1",
            "type": "default",
            "enabled": True
        },
        {
            "key": "latest_order_number",
            "value": "ORD-SAMPLE",
            "type": "default",
            "enabled": True
        },
        {
            "key": "fail_order_id",
            "value": "",
            "type": "default",
            "enabled": True
        },
        {
            "key": "fail_order_number",
            "value": "",
            "type": "default",
            "enabled": True
        },
        {
            "key": "stock_before_fail",
            "value": "0",
            "type": "default",
            "enabled": True
        },
        {
            "key": "idem_first_order_id",
            "value": "",
            "type": "default",
            "enabled": True
        }
    ],
    "_postman_variable_scope": "environment"
}

env_path = r"docs/Omnichannel_Ecommerce_Platform.postman_environment.json"
with open(env_path, "w", encoding="utf-8") as f:
    json.dump(environment, f, indent=2, ensure_ascii=False)
print("Saved environment successfully:", env_path)
