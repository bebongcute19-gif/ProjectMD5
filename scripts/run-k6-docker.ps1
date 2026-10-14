[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# ==============================================================================
# Script chạy kiểm thử tải k6 sử dụng Docker Image chính thức (grafana/k6)
# ==============================================================================
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host " CHẠY KIỂM THỬ TẢI FLASH SALE BẰNG K6 (500 - 1000 RPS) QUA DOCKER" -ForegroundColor Cyan
Write-Host "==========================================================="

# Kiểm tra xem k6 đã được cài đặt cục bộ hay chưa
$hasLocalK6 = Get-Command k6 -ErrorAction SilentlyContinue

if ($hasLocalK6) {
    Write-Host ">>> Đang chạy bằng công cụ k6 cục bộ trên máy..." -ForegroundColor Green
    k6 run .\scripts\load-test-k6.js
} else {
    Write-Host ">>> Không tìm thấy k6 cục bộ. Chạy bằng container Docker chính thức (grafana/k6)..." -ForegroundColor Yellow
    # Trên Windows Docker Desktop, container kết nối về dịch vụ trên máy host qua host.docker.internal
    Get-Content .\scripts\load-test-k6.js | docker run --rm -i -e GATEWAY_URL=http://host.docker.internal:8080 grafana/k6 run -
}
