[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# scripts/clean-for-submission.ps1
# Script dọn dẹp tất cả thư mục target/ và file build tạm trước khi nén zip nộp đồ án

$rootDir = Split-Path -Parent $PSScriptRoot

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  DỌN DẸP CÁC FILE BUILD TẠM ĐỂ CHUẨN BỊ NỘP ĐỒ ÁN  " -ForegroundColor Cyan
Write-Host "===========================================================`n" -ForegroundColor Cyan

$targets = Get-ChildItem -Path $rootDir -Include "target" -Recurse -Directory -Force -ErrorAction SilentlyContinue

$count = 0
foreach ($t in $targets) {
    Write-Host "  [X] Đang xóa thư mục: $($t.FullName)" -ForegroundColor DarkGray
    Remove-Item -Path $t.FullName -Recurse -Force -ErrorAction SilentlyContinue
    $count++
}

Write-Host "`n[HOÀN TẤT] Đã dọn sạch $count thư mục build tạm! Mã nguồn sẵn sàng để nén ZIP nộp bài." -ForegroundColor Green
