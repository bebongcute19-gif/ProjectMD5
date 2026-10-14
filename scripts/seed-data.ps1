[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/seed-data.ps1
# Script tự động khởi tạo dữ liệu mẫu cho Hệ thống Thương mại Điện tử Đa kênh
# Tạo 10 tài khoản người dùng thực tế và 14 sản phẩm, đồng bộ tồn kho giữa PostgreSQL và Redis

$ErrorActionPreference = "Continue"
$gatewayUrl = "http://localhost:8080/api/v1"

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  NOVA COMMERCE - KHỞI TẠO DỮ LIỆU (NGƯỜI DÙNG & SẢN PHẨM)  " -ForegroundColor Cyan
Write-Host "===========================================================`n" -ForegroundColor Cyan

# 1. Đọc dữ liệu từ file JSON
$jsonPath = Join-Path $PSScriptRoot "seed-data.json"
if (-not (Test-Path $jsonPath)) {
    Write-Host "[LỖI] Không tìm thấy file seed-data.json tại đường dẫn: $jsonPath" -ForegroundColor Red
    exit 1
}

$seedData = Get-Content -Path $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
$usersToSeed = $seedData.users
$productsToSeed = $seedData.products

# -----------------------------------------------------------------------------
# BƯỚC 1: Đăng nhập tài khoản Quản trị viên (Admin)
# -----------------------------------------------------------------------------
Write-Host "[1/3] Đang đăng nhập tài khoản Quản trị viên (Admin)..." -ForegroundColor Yellow
$adminAuthBody = @{
    email    = "admin@ecommerce.com"
    password = "Admin@123"
} | ConvertTo-Json

try {
    $adminRes = Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $adminAuthBody -ContentType "application/json"
    $adminToken = $adminRes.data.token
    $adminHeaders = @{
        "Authorization" = "Bearer $adminToken"
        "Content-Type"  = "application/json"
    }
    Write-Host "[THÀNH CÔNG] Đăng nhập Admin thành công! Đã lấy được mã xác thực JWT Token." -ForegroundColor Green
} catch {
    Write-Host "[THẤT BẠI] Không thể đăng nhập Admin: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# -----------------------------------------------------------------------------
# BƯỚC 2: Khởi tạo danh sách người dùng ($($usersToSeed.Count) tài khoản)
# -----------------------------------------------------------------------------
Write-Host "`n[2/3] Đang tạo $($usersToSeed.Count) tài khoản người dùng..." -ForegroundColor Yellow

$uIndex = 1
foreach ($u in $usersToSeed) {
    if ($u.email -eq "admin@ecommerce.com") {
        Write-Host "  ($uIndex/$($usersToSeed.Count)) [ADMIN HỆ THỐNG] $($u.fullName) - $($u.email) (Tài khoản Quản trị mặc định)" -ForegroundColor Cyan
        $uIndex++
        continue
    }
    try {
        $regBody = $u | ConvertTo-Json
        $res = Invoke-RestMethod -Uri "$gatewayUrl/auth/register" -Method Post -Body $regBody -ContentType "application/json"
        Write-Host "  ($uIndex/$($usersToSeed.Count)) [TẠO MỚI] $($u.fullName) - $($u.email) (Vai trò: $($u.role))" -ForegroundColor Green
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        if ($statusCode -eq 400 -or $statusCode -eq 409) {
            Write-Host "  ($uIndex/$($usersToSeed.Count)) [ĐÃ TỒN TẠI] $($u.fullName) - $($u.email) (Vai trò: $($u.role))" -ForegroundColor DarkGray
        } else {
            Write-Host "  ($uIndex/$($usersToSeed.Count)) [LỖI ĐĂNG KÝ (Mã HTTP $statusCode)]: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    $uIndex++
}

# -----------------------------------------------------------------------------
# BƯỚC 3: Khởi tạo $($productsToSeed.Count) sản phẩm & Đồng bộ kho (PostgreSQL + Redis)
# -----------------------------------------------------------------------------
Write-Host "`n[3/3] Đang tạo $($productsToSeed.Count) sản phẩm & Đồng bộ kho hàng..." -ForegroundColor Yellow

$existingProductsRes = Invoke-RestMethod -Uri "$gatewayUrl/products" -Method Get
$existingProducts = $existingProductsRes.data

$pIndex = 1
foreach ($p in $productsToSeed) {
    $existing = $existingProducts | Where-Object { $_.sku -eq $p.sku }
    $productId = $null

    if ($existing) {
        $productId = $existing.id
        Write-Host "  ($pIndex/$($productsToSeed.Count)) [ĐÃ TỒN TẠI] Sản phẩm #$productId - $($p.name)" -ForegroundColor Cyan
    } else {
        try {
            $createBody = $p | ConvertTo-Json -Depth 5
            $utf8Bytes = [System.Text.Encoding]::UTF8.GetBytes($createBody)
            $createdRes = Invoke-RestMethod -Uri "$gatewayUrl/products" -Method Post -Body $utf8Bytes -Headers $adminHeaders -ContentType "application/json; charset=utf-8"
            $productId = $createdRes.data.id
            Write-Host "  ($pIndex/$($productsToSeed.Count)) [TẠO MỚI] Sản phẩm #$productId - $($p.name)" -ForegroundColor Green
        } catch {
            Write-Host "  ($pIndex/$($productsToSeed.Count)) [LỖI TẠO] $($p.name): $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    # Đảm bảo tồn kho tại Order Service được đồng bộ trên cả PostgreSQL và Redis Cache
    if ($productId) {
        try {
            $invUrl = "$gatewayUrl/orders/admin/inventory/$productId"
            $invBody = @{ stock = [int]$p.stock } | ConvertTo-Json
            $invBytes = [System.Text.Encoding]::UTF8.GetBytes($invBody)
            $invRes = Invoke-RestMethod -Uri $invUrl -Method Put -Body $invBytes -Headers $adminHeaders -ContentType "application/json; charset=utf-8"
            Write-Host "         -> Đã đồng bộ kho Order Service: #$productId = $($p.stock) cái (PostgreSQL + Redis)" -ForegroundColor DarkGray
        } catch {
            Write-Host "         -> [Lỗi đồng bộ kho #$productId]: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    $pIndex++
}

# -----------------------------------------------------------------------------
# Báo cáo tổng kết
# -----------------------------------------------------------------------------
$finalProducts = (Invoke-RestMethod -Uri "$gatewayUrl/products" -Method Get).data
Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  HOÀN TẤT KHỞI TẠO DỮ LIỆU THÀNH CÔNG!  " -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  * Tổng số tài khoản người dùng : $($usersToSeed.Count) tài khoản (Mật khẩu: Customer@123 / Admin: Admin@123)" -ForegroundColor White
Write-Host "  * Tổng số sản phẩm hiển thị    : $($finalProducts.Count) sản phẩm (Đã đồng bộ kho PostgreSQL + Redis)" -ForegroundColor White
Write-Host "===========================================================`n" -ForegroundColor Cyan
