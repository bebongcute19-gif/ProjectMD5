[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/test-flash-sale.ps1
# Script PowerShell kiểm thử tải đồng thời sự kiện Flash Sale trên Windows
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  NOVA COMMERCE - KIỂM THỬ TẢI ĐỒNG THỜI FLASH SALE  " -ForegroundColor Yellow
Write-Host "===========================================================" -ForegroundColor Cyan

$gatewayUrl = "http://localhost:8080/api/v1"
$productId = 1
$initialStock = 10
$concurrentUsers = 50

# Bước 0: Xác thực và lấy JWT Token cho Admin và Khách hàng
Write-Host "`n[0/3] Đang đăng nhập tài khoản Quản trị viên & Khách hàng..." -ForegroundColor Yellow
$adminAuth = Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body (@{ email = "admin@ecommerce.com"; password = "Admin@123" } | ConvertTo-Json) -ContentType "application/json"
$adminToken = $adminAuth.data.token

$userAuth = Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body (@{ email = "customer@ecommerce.com"; password = "Customer@123" } | ConvertTo-Json) -ContentType "application/json"
$userToken = $userAuth.data.token
Write-Host "      Đăng nhập THÀNH CÔNG! Đã lấy JWT Token hợp lệ." -ForegroundColor Green

# Bước 1: Khởi tạo lại tồn kho
Write-Host "`n[1/3] Đặt lại số lượng tồn kho Sản phẩm #$productId về $initialStock cái..." -ForegroundColor Yellow
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
try {
    $resetResp = Invoke-RestMethod -Uri "$gatewayUrl/orders/benchmark/reset-stock?productId=$productId&stock=$initialStock" -Method Post -Headers $adminHeaders
    Write-Host "      $($resetResp.message)" -ForegroundColor Green
} catch {
    Write-Host "      [!] Lỗi đặt lại kho: $($_.Exception.Message)" -ForegroundColor Red
}

# Bước 2: Bắn đồng thời 50 request vào API Gateway
Write-Host "`n[2/3] Bắn đồng thời $concurrentUsers yêu cầu tạo đơn vào API Gateway..." -ForegroundColor Yellow
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$jobs = @()
for ($i = 1; $i -le $concurrentUsers; $i++) {
    $body = @{
        items = @(
            @{
                productId = $productId
                quantity = 1
            }
        )
    } | ConvertTo-Json -Depth 5

    $jobs += [powershell]::Create().AddScript({
        param($url, $payload, $token)
        try {
            $headers = @{
                Authorization = "Bearer $token"
            }
            $resp = Invoke-RestMethod -Uri "$url/orders" -Method Post -Body $payload -Headers $headers -ContentType "application/json"
            return @{ Status = 201; OrderNumber = $resp.data.orderNumber }
        } catch {
            return @{ Status = 409; Error = $_.Exception.Message }
        }
    }).AddArgument($gatewayUrl).AddArgument($body).AddArgument($userToken)
}

$asyncHandles = $jobs | ForEach-Object { $_.BeginInvoke() }

$successCount = 0
$rejectedCount = 0

for ($i = 0; $i -lt $jobs.Count; $i++) {
    $res = $jobs[$i].EndInvoke($asyncHandles[$i])
    $jobs[$i].Dispose()
    if ($res.Status -eq 201) {
        $successCount++
    } else {
        $rejectedCount++
    }
}

$stopwatch.Stop()

# Bước 3: Xuất báo cáo kết quả kiểm thử
Write-Host "`n[3/3] Kết quả kiểm thử tải đồng thời Flash Sale:" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------"
Write-Host "  - Tổng số request đồng thời   : $concurrentUsers"
Write-Host "  - Số đơn tạo THÀNH CÔNG       : $successCount (Kỳ vọng: $initialStock đơn)" -ForegroundColor Green
Write-Host "  - Số đơn bị TỪ CHỐI (Hết hàng): $rejectedCount (Kỳ vọng: $($concurrentUsers - $initialStock) đơn)" -ForegroundColor Magenta
Write-Host "  - Thời gian xử lý hoàn tất    : $($stopwatch.ElapsedMilliseconds) ms"
Write-Host "-----------------------------------------------------------"

try {
    $stats = Invoke-RestMethod -Uri "$gatewayUrl/orders/benchmark/stats?productId=$productId" -Method Get -Headers $adminHeaders
    $remainingStock = $stats.data.currentStock
    Write-Host "  - Tồn kho PostgreSQL thực tế  : $remainingStock" -ForegroundColor Yellow

    if ($remainingStock -eq 0 -and $successCount -eq $initialStock) {
        Write-Host "`n  [THÀNH CÔNG] KHÔNG XẢY RA RACE CONDITION, KHÔNG BỊ BÁN ÂM KHO (Âm kho = 0)!" -ForegroundColor Green
        Write-Host "  Cơ chế Atomic Lua Script trừ trước + Khóa Redis + SQL cập nhật có điều kiện hoạt động hoàn hảo!" -ForegroundColor Green
    }
} catch {
    Write-Host "  [!] Không thể lấy thống kê: $($_.Exception.Message)"
}
