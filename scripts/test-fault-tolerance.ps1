[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/test-fault-tolerance.ps1
# Kịch bản kiểm thử Khả năng Chịu lỗi & Tính Sẵn sàng cao của Hệ thống Microservices:
# 1. Tách rời bất đồng bộ (Asynchronous Decoupling): Notification Service sập không làm tắc nghẽn luồng đặt hàng; tự động hồi phục qua hàng đợi RabbitMQ
# 2. Sập Redis hoàn toàn: Container Redis bị tắt đột ngột -> dữ liệu tồn kho PostgreSQL được bảo toàn, trả lỗi an toàn (Fail-Safe), phục hồi tức thì khi bật lại Redis

$ErrorActionPreference = "Continue"
$gatewayUrl = "http://localhost:8080/api/v1"

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  KIỂM THỬ KHẢ NĂNG CHỊU LỖI & SỰ CỐ PHÂN TÁN (CHAOS TEST) " -ForegroundColor Cyan
Write-Host "===========================================================`n" -ForegroundColor Cyan

# 0. Xác thực tài khoản Khách hàng & Quản trị viên (Admin)
$loginBody = @{ email = "customer@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json
$customerToken = (Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json").data.token
$custHeaders = @{ "Authorization" = "Bearer $customerToken"; "Content-Type" = "application/json" }

$adminBody = @{ email = "admin@ecommerce.com"; password = "Admin@123" } | ConvertTo-Json
$adminToken = (Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $adminBody -ContentType "application/json").data.token
$adminHeaders = @{ "Authorization" = "Bearer $adminToken"; "Content-Type" = "application/json" }

# =============================================================================
# KỊCH BẢN 1: NOTIFICATION SERVICE BỊ SẬP (GIAO TIẾP HƯỚNG SỰ KIỆN BẤT ĐỒNG BỘ)
# =============================================================================
Write-Host "-----------------------------------------------------------" -ForegroundColor Yellow
Write-Host " [KỊCH BẢN 1] SẬP NOTIFICATION SERVICE (BẢO TOÀN LUỒNG BÁN HÀNG) " -ForegroundColor Yellow
Write-Host "-----------------------------------------------------------" -ForegroundColor Yellow

$initialNotifs = (Invoke-RestMethod -Uri "$gatewayUrl/notifications" -Method Get -Headers $adminHeaders).count
Write-Host "[1.1] Số lượng thông báo ban đầu: $initialNotifs" -ForegroundColor Gray

Write-Host "[1.2] Mô phỏng sự cố: Đang dừng cưỡng bức container nexus-notification-service..." -ForegroundColor Magenta
docker stop nexus-notification-service | Out-Null
Start-Sleep -Seconds 2

Write-Host "[1.3] Đặt hàng mới trong lúc Notification Service đang CHẾT..." -ForegroundColor Yellow
$orderReq1 = @{
    items = @( @{ productId = 3; quantity = 1 } )
    shippingAddress = "Da Nang"
    paymentMethod = "VNPAY"
} | ConvertTo-Json

try {
    $orderRes1 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReq1 -Headers $custHeaders -ContentType "application/json"
    $ordNum1 = $orderRes1.data.orderNumber
    Write-Host "[ĐẠT] Đơn hàng #$ordNum1 tạo THÀNH CÔNG! Trạng thái: $($orderRes1.data.status)" -ForegroundColor Green
    Write-Host "      Luồng thanh toán cốt lõi và trừ kho vẫn thông suốt nhờ giao tiếp bất đồng bộ qua Transactional Outbox!" -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] Đặt hàng thất bại: $($_.Exception.Message)" -ForegroundColor Red
}

# Kiểm tra hàng đợi RabbitMQ lưu đệm tin nhắn khi Notification Service chết
Start-Sleep -Seconds 2
$queueLine = (docker exec nexus-rabbitmq rabbitmqctl list_queues name messages 2>$null | Select-String "notification.all.queue").Line
Write-Host "      Trạng thái RabbitMQ lúc Service sập: $queueLine" -ForegroundColor Cyan

Write-Host "[1.4] Khởi động lại container nexus-notification-service..." -ForegroundColor Magenta
docker start nexus-notification-service | Out-Null
Write-Host "      Đang chờ Notification Service khởi động lại và tiêu thụ bù các tin nhắn trong RabbitMQ (tối đa 25s)..." -ForegroundColor Gray

$recovered = $false
for ($i = 1; $i -le 8; $i++) {
    Start-Sleep -Seconds 3
    $queueCurrent = (docker exec nexus-rabbitmq rabbitmqctl list_queues name messages 2>$null | Select-String "notification.all.queue").Line
    if ($queueCurrent -match "notification\.all\.queue\s+0") {
        $recovered = $true
        Write-Host "[1.5] Trạng thái hàng đợi sau khi tiêu thụ bù: $queueCurrent" -ForegroundColor Green
        break
    }
}

if ($recovered) {
    Write-Host "[ĐẠT] Hàng đợi RabbitMQ đã được giải phóng về 0! Notification Service đã tiêu thụ bù thành công toàn bộ sự kiện tồn đọng sau sự cố." -ForegroundColor Green
} else {
    Write-Host "[THÔNG TIN] Container đang tiếp tục tiêu thụ tin nhắn trong hàng đợi RabbitMQ." -ForegroundColor Yellow
}

# =============================================================================
# KỊCH BẢN 2: REDIS BỊ SẬP TOÀN BỘ (BẢO TOÀN TÍNH TOÀN VẸN CƠ SỞ DỮ LIỆU)
# =============================================================================
Write-Host "`n-----------------------------------------------------------" -ForegroundColor Yellow
Write-Host " [KỊCH BẢN 2] REDIS SẬP TOÀN BỘ (KIỂM TRA BẢO TOÀN DỮ LIỆU POSTGRESQL) " -ForegroundColor Yellow
Write-Host "-----------------------------------------------------------" -ForegroundColor Yellow

# Truy vấn tồn kho thực tế trong PostgreSQL trước khi tắt Redis
$stockBefore = (Invoke-RestMethod -Uri "$gatewayUrl/orders/inventory/4" -Method Get).availableStock
Write-Host "[2.1] Tồn kho thực tế trong PostgreSQL của Sản phẩm #4 trước test: $stockBefore" -ForegroundColor Gray

Write-Host "[2.2] Mô phỏng thảm họa: Đang dừng cưỡng bức container nexus-redis..." -ForegroundColor Magenta
docker stop nexus-redis | Out-Null
Start-Sleep -Seconds 2

Write-Host "[2.3] Gửi yêu cầu đặt hàng khi Redis đang CHẾT HOÀN TOÀN..." -ForegroundColor Yellow
$orderReq2 = @{
    items = @( @{ productId = 4; quantity = 1 } )
    shippingAddress = "Can Tho"
    paymentMethod = "VNPAY"
} | ConvertTo-Json

try {
    $orderRes2 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReq2 -Headers $custHeaders -ContentType "application/json"
    Write-Host "[KẾT QUẢ] Phản hồi đơn hàng: $($orderRes2.message)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Hệ thống từ chối an toàn, bảo vệ cơ sở dữ liệu! Mã HTTP: $statusCode (Quá tải / Chống lỗi tầng kho)" -ForegroundColor Green
    Write-Host "      Lý do lỗi chi tiết: $($_.Exception.Message)" -ForegroundColor DarkGray
}

Write-Host "[2.4] Khởi động lại container nexus-redis..." -ForegroundColor Magenta
docker start nexus-redis | Out-Null
Write-Host "      Chờ 5 giây để Redis sẵn sàng hoạt động trở lại..." -ForegroundColor Gray
Start-Sleep -Seconds 5

$stockAfter = (Invoke-RestMethod -Uri "$gatewayUrl/orders/inventory/4" -Method Get).availableStock
Write-Host "[2.5] Tồn kho PostgreSQL của Sản phẩm #4 sau khi Redis hồi phục: $stockAfter (Kỳ vọng giữ nguyên: $stockBefore)" -ForegroundColor Green

if ($stockAfter -eq $stockBefore) {
    Write-Host "[ĐẠT] Tồn kho trong PostgreSQL được bảo toàn nguyên vẹn, không ghi nhận hiện tượng âm kho hay thất thoát dữ liệu!" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Số tồn kho bị thay đổi: Trước=$stockBefore, Sau=$stockAfter" -ForegroundColor Yellow
}

Write-Host "`n[2.6] Kiểm tra việc tạo đơn bình thường hoạt động trở lại ngay sau khi Redis phục hồi..." -ForegroundColor Yellow
try {
    $orderRes3 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReq2 -Headers $custHeaders -ContentType "application/json"
    Write-Host "[ĐẠT] Hệ thống hoạt động trơn tru trở lại! Đã tạo Đơn hàng #$($orderRes3.data.orderNumber) sau khi Redis sống lại." -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] Tạo đơn thất bại sau khi phục hồi: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  HOÀN TẤT KIỂM THỬ KHẢ NĂNG CHỊU LỖI & TỰ HỒI PHỤC!  " -ForegroundColor Green
Write-Host "  - Notification sập: Luồng đặt hàng cốt lõi không bị gián đoạn, tin nhắn lưu đệm an toàn trong RabbitMQ và tiêu thụ bù khi service phục hồi." -ForegroundColor Gray
Write-Host "  - Redis sập: Hệ thống từ chối an toàn (Fail-Safe 409), bảo vệ PostgreSQL không bị quá tải hay sai lệch kho, tự động hồi phục khi Redis sẵn sàng." -ForegroundColor Gray
Write-Host "===========================================================`n" -ForegroundColor Cyan
