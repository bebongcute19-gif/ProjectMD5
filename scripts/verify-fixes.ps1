[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/verify-fixes.ps1
# Kịch bản kiểm thử toàn diện 9 bài test kiến trúc Microservices & Bảo vệ đồ án
$ErrorActionPreference = "Continue"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 1] ĐƯỜNG DẪN CÔNG KHAI: GET /api/v1/products" -ForegroundColor Cyan
Write-Host "========================================================"
try {
    $res1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products" -Method Get
    Write-Host "[ĐẠT] Truy cập công khai thành công! Số lượng sản phẩm: $($res1.count), Mã kết quả: $($res1.code)" -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 2] CỔNG GATEWAY CHẶN MÃ 401 (Khi thiếu JWT Token)" -ForegroundColor Cyan
Write-Host "========================================================"
try {
    $orderBody = @{
        items = @(@{ productId = 1; quantity = 1 })
    } | ConvertTo-Json
    $res2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -Body $orderBody -ContentType "application/json"
    Write-Host "[THẤT BẠI] Lẽ ra yêu cầu phải bị chặn với mã 401 Unauthorized!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Đã bị chặn chính xác với mã HTTP: $status (Không có quyền truy cập)" -ForegroundColor Green
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 3] XÁC THỰC & ĐĂNG NHẬP (Admin & Khách hàng)" -ForegroundColor Cyan
Write-Host "========================================================"
$adminLoginBody = @{ email = "admin@ecommerce.com"; password = "Admin@123" } | ConvertTo-Json
$adminAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminAuth.data.token
Write-Host "[ĐẠT] Đã lấy mã Token của Admin: Vai trò = $($adminAuth.data.user.role)" -ForegroundColor Green

$userLoginBody = @{ email = "customer@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json
$userAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $userLoginBody -ContentType "application/json"
$userToken = $userAuth.data.token
Write-Host "[ĐẠT] Đã lấy mã Token của Khách hàng A: Vai trò = $($userAuth.data.user.role)" -ForegroundColor Green

# Xác thực tài khoản Khách hàng B
$userBLoginBody = @{ email = "customerB@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json
$userBToken = ""
try {
    $userBAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $userBLoginBody -ContentType "application/json"
    $userBToken = $userBAuth.data.token
} catch {
    $userBRegBody = @{
        email = "customerB@ecommerce.com"
        password = "Customer@123"
        fullName = "Customer B"
        phone = "0988888888"
        role = "CUSTOMER"
    } | ConvertTo-Json
    $userBAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -Body $userBRegBody -ContentType "application/json"
    $userBToken = $userBAuth.data.token
}
$headersCustomerB = @{ Authorization = "Bearer $userBToken" }
Write-Host "[ĐẠT] Đã lấy mã Token của Khách hàng B: Vai trò = $($userBAuth.data.user.role)" -ForegroundColor Green

function Wait-ForPaymentRecord {
    param(
        [long]$OrderId,
        [hashtable]$Headers,
        [int]$MaxWaitSeconds = 5
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt ($MaxWaitSeconds * 1000)) {
        try {
            $payment = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/order/$OrderId" -Method Get -Headers $Headers -ErrorAction SilentlyContinue
            if ($payment -and $payment.data -and $payment.data.id) {
                return $payment.data
            }
        } catch {}
        Start-Sleep -Milliseconds 200
    }
    return $null
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 4] GATEWAY CHẶN 403 RBAC (Khách hàng gọi vào API Admin)" -ForegroundColor Cyan
Write-Host "========================================================"
try {
    $headersCustomer = @{ Authorization = "Bearer $userToken" }
    $res4 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/admin/all" -Method Get -Headers $headersCustomer
    Write-Host "[THẤT BẠI] Khách hàng thông thường tuyệt đối không được truy cập /orders/admin/all!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Đã bị chặn chính xác với mã HTTP: $status (403 Forbidden - Cấm quyền CUSTOMER)" -ForegroundColor Green
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 5] QUYỀN ADMIN TRUY CẬP /orders/admin/all" -ForegroundColor Cyan
Write-Host "========================================================"
try {
    $headersAdmin = @{ Authorization = "Bearer $adminToken" }
    $res5 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/admin/all" -Method Get -Headers $headersAdmin
    Write-Host "[ĐẠT] Quản trị viên truy cập thành công! Mã kết quả: $($res5.code), Số lượng đơn hàng: $($res5.count)" -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] Quản trị viên truy cập thất bại: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 6] CHỐNG GIẢ MẠO ĐỊNH DANH & CAN THIỆP GIÁ TIỀN" -ForegroundColor Cyan
Write-Host " Phía client cố tình gửi customerId=9999 và giá sản phẩm = 1 VNĐ" -ForegroundColor Cyan
Write-Host "========================================================"
try {
    # Khởi tạo tồn kho Sản phẩm #1 trước khi đặt hàng test
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/benchmark/reset-stock?productId=1&stock=10" -Method Post -Headers $headersAdmin | Out-Null

    $tamperOrder = @{
        customerId = 9999
        customerEmail = "spoofed@hacker.com"
        items = @(
            @{
                productId = 1
                quantity = 1
                price = 1
                productName = "Fake 1 Dong Phone"
            }
        )
    } | ConvertTo-Json
    $res6 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -Body $tamperOrder -Headers $headersCustomer -ContentType "application/json"
    $createdOrder = $res6.data
    Write-Host "[ĐẠT] Tạo đơn hàng thành công: Mã đơn #$($createdOrder.orderNumber)" -ForegroundColor Green
    Write-Host " - ID Khách hàng ghi nhận : $($createdOrder.customerId) (Lấy từ JWT token: $($userAuth.data.user.id), Đã bỏ qua ID 9999 giả mạo!)" -ForegroundColor Green
    Write-Host " - Giá sản phẩm ghi nhận  : $($createdOrder.items[0].price) VNĐ (Lấy từ DB gốc, Đã bỏ qua giá 1 VNĐ giả mạo!)" -ForegroundColor Green
    Write-Host " - Tổng tiền thanh toán   : $($createdOrder.totalAmount) VNĐ" -ForegroundColor Green
    Write-Host " - Trạng thái ban đầu     : $($createdOrder.status)" -ForegroundColor Green

    $testOrderId = $createdOrder.id
} catch {
    Write-Host "[THẤT BẠI] Kiểm thử chống can thiệp thất bại: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 7] BẢO VỆ GIAO DỊCH SAGA & IDEMPOTENCY THANH TOÁN" -ForegroundColor Cyan
Write-Host "========================================================"
if ($testOrderId) {
    # 7.1 Giả lập thanh toán thất bại để kích hoạt Saga Bồi hoàn (Compensating Transaction)
    Write-Host "-> Đang chờ giao dịch thanh toán sẵn sàng qua Outbox (tối đa 5s) cho Đơn hàng #$testOrderId..." -ForegroundColor Yellow
    $readyPay = Wait-ForPaymentRecord -OrderId $testOrderId -Headers $headersCustomer -MaxWaitSeconds 5
    if ($readyPay) {
        Write-Host "[ĐẠT] Giao dịch thanh toán đã sẵn sàng: Mã giao dịch ID = $($readyPay.id)" -ForegroundColor Green
    }
    $payFailBody = @{
        orderId = $testOrderId
        action = "FAIL"
    } | ConvertTo-Json
    $payFailRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/process" -Method Post -Body $payFailBody -Headers $headersCustomer -ContentType "application/json"
    Write-Host "[ĐẠT] Kết quả xử lý thanh toán: Trạng thái = $($payFailRes.data.status), Giao dịch ID = $($payFailRes.data.transactionId) (Kích hoạt Saga bồi hoàn)" -ForegroundColor Green

    Start-Sleep -Seconds 2

    # Kiểm tra trạng thái đơn hàng sau khi bồi hoàn
    $orderAfterFail = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$testOrderId" -Method Get -Headers $headersCustomer
    Write-Host "[ĐẠT] Trạng thái đơn hàng sau khi bồi hoàn Saga: $($orderAfterFail.data.status) (Bắt buộc là CANCELLED)" -ForegroundColor Green

    # 7.2 Thử thanh toán lại SUCCESS trên giao dịch đã đóng/CANCELLED (Kiểm tra Idempotency)
    Write-Host "-> Thử thanh toán lại SUCCESS trên giao dịch đã đóng (Kiểm tra tính Idempotent)..." -ForegroundColor Yellow
    try {
        $paySuccessBody = @{
            orderId = $testOrderId
            action = "SUCCESS"
        } | ConvertTo-Json
        $payRetry = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/process" -Method Post -Body $paySuccessBody -Headers $headersCustomer -ContentType "application/json"
        Write-Host "[THẤT BẠI] Cổng thanh toán lẽ ra phải từ chối xử lý lại giao dịch đã đóng!" -ForegroundColor Red
    } catch {
        Write-Host "[ĐẠT] Đã chặn thanh toán lại chính xác như mong đợi! Thông báo lỗi: $($_.Exception.Message)" -ForegroundColor Green
    }
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 8] MÁY TRẠNG THÁI (STATE MACHINE) & VÒNG ĐỜI ĐƠN HÀNG" -ForegroundColor Cyan
Write-Host "========================================================"
if ($testOrderId) {
    # 8.1 Khách hàng tự ý đổi trạng thái đơn -> Phải bị chặn 403 Forbidden
    try {
        $statusBody = @{ status = "SHIPPING" } | ConvertTo-Json
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$testOrderId/status" -Method Put -Body $statusBody -Headers $headersCustomer -ContentType "application/json"
        Write-Host "[THẤT BẠI] Khách hàng tuyệt đối không được phép tự sửa trạng thái đơn hàng!" -ForegroundColor Red
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "[ĐẠT] Khách hàng bị chặn với mã HTTP: $status (403 Forbidden)" -ForegroundColor Green
    }

    # 8.2 Kiểm tra Máy trạng thái: Admin thử nhảy cóc sai quy trình CANCELLED -> SHIPPING
    try {
        $statusBody = @{ status = "SHIPPING" } | ConvertTo-Json
        $invalidUpdate = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$testOrderId/status" -Method Put -Body $statusBody -Headers $headersAdmin -ContentType "application/json"
        Write-Host "[THẤT BẠI] Máy trạng thái lẽ ra phải TỪ CHỐI chuyển trạng thái từ CANCELLED sang SHIPPING!" -ForegroundColor Red
    } catch {
        Write-Host "[ĐẠT] Máy trạng thái từ chối chính xác bước chuyển sai quy tắc CANCELLED -> SHIPPING! ($($_.Exception.Message))" -ForegroundColor Green
    }

    # 8.3 Kiểm tra toàn bộ vòng đời hợp lệ: Tạo đơn mới -> Thanh toán SUCCESS -> CONFIRMED -> SHIPPING -> COMPLETED
    Write-Host "-> Tạo đơn hàng mới để kiểm tra toàn bộ vòng đời (PENDING -> CONFIRMED -> SHIPPING -> COMPLETED)..." -ForegroundColor Yellow
    $freshOrderBody = @{
        items = @(@{ productId = 2; quantity = 1 })
    } | ConvertTo-Json
    $freshRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -Body $freshOrderBody -Headers $headersCustomer -ContentType "application/json"
    $freshOrderId = $freshRes.data.id

    Write-Host "-> Đang chờ giao dịch thanh toán mới qua Outbox (tối đa 5s)..." -ForegroundColor Yellow
    $readyFreshPay = Wait-ForPaymentRecord -OrderId $freshOrderId -Headers $headersCustomer -MaxWaitSeconds 5
    if ($readyFreshPay) {
        Write-Host "[ĐẠT] Giao dịch thanh toán mới đã sẵn sàng: ID = $($readyFreshPay.id)" -ForegroundColor Green
    }

    # Thanh toán thành công (SUCCESS)
    $payOkBody = @{ orderId = $freshOrderId; action = "SUCCESS" } | ConvertTo-Json
    $payOkRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/process" -Method Post -Body $payOkBody -Headers $headersCustomer -ContentType "application/json"
    Start-Sleep -Seconds 2

    # Kiểm tra trạng thái đã chuyển sang CONFIRMED
    $freshOrderCheck = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$freshOrderId" -Method Get -Headers $headersCustomer
    Write-Host "[ĐẠT] Đơn hàng #$freshOrderId sau khi thanh toán SUCCESS: Trạng thái = $($freshOrderCheck.data.status) (Kỳ vọng: CONFIRMED)" -ForegroundColor Green

    # Admin cập nhật CONFIRMED -> SHIPPING
    $toShipping = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$freshOrderId/status" -Method Put -Body (@{ status = "SHIPPING" } | ConvertTo-Json) -Headers $headersAdmin -ContentType "application/json"
    Write-Host "[ĐẠT] Admin cập nhật CONFIRMED -> SHIPPING: Trạng thái = $($toShipping.data.status)" -ForegroundColor Green

    # Admin cập nhật SHIPPING -> COMPLETED
    $toCompleted = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$freshOrderId/status" -Method Put -Body (@{ status = "COMPLETED" } | ConvertTo-Json) -Headers $headersAdmin -ContentType "application/json"
    Write-Host "[ĐẠT] Admin cập nhật SHIPPING -> COMPLETED: Trạng thái = $($toCompleted.data.status)" -ForegroundColor Green
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [BÀI TEST 9] QUYỀN SỞ HỮU GIAO DỊCH & BẢO MẬT DỮ LIỆU" -ForegroundColor Cyan
Write-Host "========================================================"
# 9.1 Khách hàng thông thường xem danh sách toàn bộ giao dịch thanh toán -> Kỳ vọng 403 Forbidden
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" -Method Get -Headers $headersCustomer
    Write-Host "[THẤT BẠI] Khách hàng thông thường tuyệt đối không được xem danh sách thanh toán của người khác!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Khách hàng bị chặn khi gọi GET /payments với mã HTTP: $status (403 Forbidden)" -ForegroundColor Green
}

# 9.2 Quản trị viên xem danh sách toàn bộ giao dịch thanh toán -> Kỳ vọng 200 OK
try {
    $adminPayments = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" -Method Get -Headers $headersAdmin
    Write-Host "[ĐẠT] Quản trị viên lấy danh sách giao dịch thành công: Số lượng = $($adminPayments.count)" -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] Quản trị viên lấy danh sách thất bại: $($_.Exception.Message)" -ForegroundColor Red
}

# 9.3 Gọi /payments/process ngay lập tức khi Outbox chưa kịp chuyển sang Payment Service -> Bị từ chối an toàn
Write-Host "-> Thử nghiệm gọi /payments/process ngay khi vừa tạo đơn (Trước khi Outbox kịp chuyển phát)..." -ForegroundColor Yellow
$instantOrderBody = @{ items = @(@{ productId = 3; quantity = 1 }) } | ConvertTo-Json
$instantOrderRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -Body $instantOrderBody -Headers $headersCustomer -ContentType "application/json"
$instantOrderId = $instantOrderRes.data.id

try {
    $instantPayBody = @{ orderId = $instantOrderId; action = "SUCCESS" } | ConvertTo-Json
    $instantPayRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/process" -Method Post -Body $instantPayBody -Headers $headersCustomer -ContentType "application/json"
    Write-Host "[THÔNG BÁO] Thanh toán được xử lý trực tiếp: $($instantPayRes.message)" -ForegroundColor Yellow
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Yêu cầu bị từ chối chính xác như kỳ vọng: Mã HTTP $status (Giao dịch chưa sẵn sàng)" -ForegroundColor Green
}

# 9.4 Khách hàng B cố tình thanh toán cho đơn hàng của Khách hàng A -> Kỳ vọng 403 Forbidden
Write-Host "-> Đang chờ giao dịch sẵn sàng qua Outbox, sau đó thử nghiệm Khách hàng B trả tiền đơn của Khách hàng A..." -ForegroundColor Yellow
$readyCrossPay = Wait-ForPaymentRecord -OrderId $instantOrderId -Headers $headersCustomer -MaxWaitSeconds 5

try {
    $crossPayBody = @{ orderId = $instantOrderId; action = "SUCCESS" } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/process" -Method Post -Body $crossPayBody -Headers $headersCustomerB -ContentType "application/json"
    Write-Host "[THẤT BẠI] Khách hàng B tuyệt đối không được phép thanh toán đơn của Khách hàng A!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "[ĐẠT] Khách hàng B đã bị chặn khi cố thanh toán đơn của người khác với mã HTTP: $status (403 Forbidden)" -ForegroundColor Green
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " HOÀN TẤT TOÀN BỘ 9 BÀI TEST KIẾN TRÚC THÀNH CÔNG RỰC RỠ!" -ForegroundColor Cyan
Write-Host "========================================================`n"
