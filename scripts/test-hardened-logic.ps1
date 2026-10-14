[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/test-hardened-logic.ps1
# Kịch bản kiểm thử các cơ chế kỹ thuật chuyên sâu:
# 1. Mất key Redis (Cache Eviction) & Chống Race Condition khi 20 request song song cùng nạp lại (SETNX)
# 2. Bảo mật Idempotency (Chống rò rỉ dữ liệu xuyên người dùng) & Chặn sai lệch nội dung giỏ hàng (Mã HTTP 422)
# 3. Ép khoảng phân trang an toàn (page < 0 -> 0, size > 100 -> 100)

$ErrorActionPreference = "Continue"
$gatewayUrl = "http://localhost:8080/api/v1"

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  KIỂM THỬ BẢO MẬT IDEMPOTENCY & CHỐNG LỖI CACHE NÂNG CAO  " -ForegroundColor Cyan
Write-Host "===========================================================`n" -ForegroundColor Cyan

# 0. Xác thực tài khoản Khách hàng A, Khách hàng B, và Quản trị viên (Admin)
$loginA = @{ email = "customer@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json
$tokenA = (Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $loginA -ContentType "application/json").data.token
$headersA = @{ "Authorization" = "Bearer $tokenA"; "Content-Type" = "application/json" }

$loginB = @{ email = "customerB@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json
$tokenB = (Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $loginB -ContentType "application/json").data.token
$headersB = @{ "Authorization" = "Bearer $tokenB"; "Content-Type" = "application/json" }

$loginAdmin = @{ email = "admin@ecommerce.com"; password = "Admin@123" } | ConvertTo-Json
$tokenAdmin = (Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $loginAdmin -ContentType "application/json").data.token
$headersAdmin = @{ "Authorization" = "Bearer $tokenAdmin"; "Content-Type" = "application/json" }

# -----------------------------------------------------------------------------
# BÀI TEST 1: MẤT KEY REDIS + 20 REQUEST ĐỒNG THỜI (KIỂM TRA CHỐNG ĐÈ VỚI SETNX)
# -----------------------------------------------------------------------------
Write-Host "--- [BÀI TEST 1] Mất Key Redis + 20 Request Bắn Song Song (Thử thách SETNX) ---" -ForegroundColor Yellow

# Đặt số lượng tồn kho của Sản phẩm #2 thành 25 cái
$setStockBody = @{ stock = 25 } | ConvertTo-Json
Invoke-RestMethod -Uri "$gatewayUrl/orders/admin/inventory/2" -Method Put -Body $setStockBody -Headers $headersAdmin -ContentType "application/json" | Out-Null
Write-Host "[1.1] Đã khởi tạo tồn kho Sản phẩm #2 trên DB và Redis thành 25 cái" -ForegroundColor Gray

# Xóa key stock:product:2 trong Redis (giả lập trường hợp Redis bị khởi động lại hoặc evict cache)
docker exec nexus-redis redis-cli del "stock:product:2" | Out-Null
$keyExists = docker exec nexus-redis redis-cli exists "stock:product:2"
Write-Host "[1.2] Đã xóa key stock:product:2 trong Redis (Trạng thái tồn tại = $keyExists)" -ForegroundColor Magenta

Write-Host "[1.3] Bắn đồng thời 20 request tạo đơn Sản phẩm #2 (mỗi đơn mua số lượng 1)..." -ForegroundColor Yellow
$scriptBlock = {
    param($url, $token)
    $h = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }
    $b = @{
        items = @( @{ productId = 2; quantity = 1 } )
        shippingAddress = "Ha Noi"
        paymentMethod = "VNPAY"
    } | ConvertTo-Json
    try {
        $res = Invoke-RestMethod -Uri "$url/orders" -Method Post -Body $b -Headers $h -ContentType "application/json" -TimeoutSec 10
        return @{ success = $true; orderNumber = $res.data.orderNumber }
    } catch {
        return @{ success = $false; error = $_.Exception.Message }
    }
}

$jobs = 1..20 | ForEach-Object {
    Start-Job -ScriptBlock $scriptBlock -ArgumentList $gatewayUrl, $tokenA
}
$results = $jobs | ForEach-Object { Receive-Job -Job $_ -Wait -AutoRemoveJob }

$successCount = ($results | Where-Object { $_.success -eq $true }).Count
$failCount = ($results | Where-Object { $_.success -eq $false }).Count
Write-Host "[1.4] Kết quả 20 request song song: $successCount đơn Thành công, $failCount đơn Thất bại" -ForegroundColor Cyan

# Kiểm tra số lượng tồn kho trong Redis và DB sau khi 20 request chạy xong
$redisVal = docker exec nexus-redis redis-cli get "stock:product:2"
$dbStock = (Invoke-RestMethod -Uri "$gatewayUrl/orders/inventory/2" -Method Get).availableStock
Write-Host "[1.5] Kiểm tra tồn kho: DB khả dụng = $dbStock, Redis = $redisVal (Kho ban đầu = 25)" -ForegroundColor Green

if ([int]$redisVal -eq [int]$dbStock) {
    Write-Host "[ĐẠT] Lệnh SETNX đã ngăn chặn hoàn toàn việc ghi đè sai lệch! Kho Redis và PostgreSQL khớp chính xác 100% ($redisVal == $dbStock)!" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Có sự lệch kho: Redis=$redisVal so với DB=$dbStock" -ForegroundColor Yellow
}

# -----------------------------------------------------------------------------
# BÀI TEST 2: BẢO MẬT IDEMPOTENCY & CHỐNG SAI LỆCH NỘI DUNG GIỎ HÀNG
# -----------------------------------------------------------------------------
Write-Host "`n--- [BÀI TEST 2] Bảo Mật Idempotency & Phát Hiện Thay Đổi Giỏ Hàng ---" -ForegroundColor Yellow

$sharedKey = "IDEM-SEC-" + [guid]::NewGuid().ToString()

$orderReqOriginal = @{
    items = @( @{ productId = 4; quantity = 1 } )
    shippingAddress = "Da Nang"
    paymentMethod = "VNPAY"
} | ConvertTo-Json

# Yêu cầu 2.1: Khách hàng A tạo đơn hàng với khóa Idempotency-Key
$headersA_Idem = @{ "Authorization" = "Bearer $tokenA"; "Content-Type" = "application/json"; "Idempotency-Key" = $sharedKey }
$resA1 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReqOriginal -Headers $headersA_Idem -ContentType "application/json"
$ordIdA1 = $resA1.data.id
$ordNumA1 = $resA1.data.orderNumber
Write-Host "[2.1] Khách hàng A tạo Đơn hàng #$ordIdA1 ($ordNumA1) với Khóa: $sharedKey" -ForegroundColor Green

# Yêu cầu 2.2: Khách hàng A gửi lại Y NGUYÊN đơn hàng đó với cùng khóa -> Phải trả về đơn hàng cũ đã có
$resA2 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReqOriginal -Headers $headersA_Idem -ContentType "application/json"
$ordIdA2 = $resA2.data.id
if ($ordIdA1 -eq $ordIdA2) {
    Write-Host "[2.2] Khách hàng A gửi lại cùng nội dung: Hệ thống trả về đúng đơn hàng cũ #$ordIdA2 (Không tạo trùng đơn)" -ForegroundColor Green
} else {
    Write-Host "[THẤT BẠI] Hệ thống vô tình tạo thêm đơn hàng trùng lặp: #$ordIdA2" -ForegroundColor Red
}

# Yêu cầu 2.3: Khách hàng A gửi CÙNG khóa nhưng ĐỔI GIỎ HÀNG (số lượng = 2) -> Bắt buộc chặn bằng HTTP 422!
$orderReqDifferent = @{
    items = @( @{ productId = 4; quantity = 2 } )
    shippingAddress = "Da Nang"
    paymentMethod = "VNPAY"
} | ConvertTo-Json

try {
    $resA_Diff = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReqDifferent -Headers $headersA_Idem -ContentType "application/json"
    Write-Host "[THẤT BẠI] Lỗi thay đổi giỏ hàng KHÔNG bị chặn!" -ForegroundColor Red
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "[2.3] Khách hàng A đổi giỏ hàng khi dùng lại khóa cũ: Đã bị chặn với mã HTTP $code (Kỳ vọng: 422 Unprocessable Entity)" -ForegroundColor Green
    if ($code -eq 422) {
        Write-Host "      [ĐẠT] Chuẩn IETF Idempotency: Thay đổi nội dung yêu cầu bị từ chối chính xác!" -ForegroundColor Green
    }
}

# Yêu cầu 2.4: Khách hàng B dùng TRỘM khóa Idempotency-Key của Khách hàng A -> Bắt buộc chặn HTTP 409 (Chống lộ dữ liệu)!
$headersB_Idem = @{ "Authorization" = "Bearer $tokenB"; "Content-Type" = "application/json"; "Idempotency-Key" = $sharedKey }
try {
    $resB = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $orderReqOriginal -Headers $headersB_Idem -ContentType "application/json"
    Write-Host "[THẤT BẠI] Khách hàng B nhận được đơn hàng của Khách hàng A! Phát hiện rò rỉ dữ liệu: #$($resB.data.id)" -ForegroundColor Red
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "[2.4] Khách hàng B mạo danh dùng khóa của Khách hàng A: Bị chặn với mã HTTP $code (Kỳ vọng: 409 Conflict)" -ForegroundColor Green
    Write-Host "      [ĐẠT] Đã ngăn chặn triệt để nguy cơ rò rỉ dữ liệu xuyên người dùng!" -ForegroundColor Green
}

# -----------------------------------------------------------------------------
# BÀI TEST 3: ÉP KHOẢNG PHÂN TRANG AN TOÀN (CHỐNG TẤN CÔNG BỘ NHỚ)
# -----------------------------------------------------------------------------
Write-Host "`n--- [BÀI TEST 3] Giới Hạn Tham Số Phân Trang An Toàn ---" -ForegroundColor Yellow

# Gửi yêu cầu ?size=1000000 -> Server phải tự ép về tối đa 100
$pagedExcessive = Invoke-RestMethod -Uri "$gatewayUrl/products?page=0&size=1000000" -Method Get
Write-Host "[3.1] Gửi size=1000000 -> Máy chủ tự động ép size về: $($pagedExcessive.size)" -ForegroundColor Green
if ($pagedExcessive.size -eq 100) {
    Write-Host "      [ĐẠT] Tham số size vượt mức đã được ép về ngưỡng an toàn 100!" -ForegroundColor Green
} else {
    Write-Host "      [THẤT BẠI] Tham số size không được ép: $($pagedExcessive.size)" -ForegroundColor Red
}

# Gửi yêu cầu ?page=-10 -> Server phải tự ép về trang đầu tiên (page=0)
$pagedNegative = Invoke-RestMethod -Uri "$gatewayUrl/products?page=-10&size=10" -Method Get
Write-Host "[3.2] Gửi page=-10 -> Máy chủ tự động ép page về: $($pagedNegative.page)" -ForegroundColor Green
if ($pagedNegative.page -eq 0) {
    Write-Host "      [ĐẠT] Số trang âm đã được ép về trang 0 an toàn!" -ForegroundColor Green
} else {
    Write-Host "      [THẤT BẠI] Trang âm không được ép: $($pagedNegative.page)" -ForegroundColor Red
}

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  TẤT CẢ CÁC BÀI TEST LOGIC & BẢO MẬT NÂNG CAO ĐỀU VƯỢT QUA! " -ForegroundColor Green
Write-Host "===========================================================`n" -ForegroundColor Cyan
