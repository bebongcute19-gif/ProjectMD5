#!/usr/bin/env python3
"""
scripts/test-flash-sale.py
High-Concurrency Flash Sale Benchmark Script for Omnichannel Microservices
Simulates 100-500 simultaneous customers buying a single item with stock = 10.
Proves Redis Distributed Lock prevents race conditions and zero negative stock.
"""

import concurrent.futures
import json
import time
import urllib.request
import urllib.error

GATEWAY_URL = "http://localhost:8080/api/v1"
PRODUCT_ID = 1
INITIAL_STOCK = 10
CONCURRENT_USERS = 100

def reset_inventory():
    print(f"\n[1/3] Đang reset tồn kho sản phẩm #{PRODUCT_ID} về {INITIAL_STOCK}...")
    url = f"{GATEWAY_URL}/orders/benchmark/reset-stock?productId={PRODUCT_ID}&stock={INITIAL_STOCK}"
    req = urllib.request.Request(url, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            print(f"      {data.get('message', 'Đã reset!')}")
    except Exception as e:
        print(f"      [!] Lỗi reset: {e}")

def place_order(user_id):
    url = f"{GATEWAY_URL}/orders"
    payload = {
        "customerId": 1000 + user_id,
        "customerEmail": f"tester_{user_id}@benchmark.local",
        "customerName": f"Tester Luồng #{user_id}",
        "shippingAddress": "Phòng Thí Nghiệm Chịu Tải",
        "phone": f"090{user_id:07d}",
        "paymentMethod": "VNPAY",
        "items": [
            {
                "productId": PRODUCT_ID,
                "productName": "iPhone 16 Pro Max (Flash Sale Concurrency)",
                "sku": "IPHONE-16-PM-256",
                "price": 19990000,
                "quantity": 1
            }
        ]
    }
    
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            latency = (time.time() - t0) * 1000
            return (resp.status, latency, None)
    except urllib.error.HTTPError as he:
        latency = (time.time() - t0) * 1000
        return (he.code, latency, he.read().decode())
    except Exception as e:
        latency = (time.time() - t0) * 1000
        return (500, latency, str(e))

def run_benchmark():
    reset_inventory()
    
    print(f"\n[2/3] Bắt đầu bắn {CONCURRENT_USERS} requests đồng thời vào API Gateway...")
    start_time = time.time()
    
    success_count = 0
    rejected_count = 0
    latencies = []
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_USERS) as executor:
        futures = [executor.submit(place_order, i) for i in range(1, CONCURRENT_USERS + 1)]
        for f in concurrent.futures.as_completed(futures):
            status, latency, body = f.result()
            latencies.append(latency)
            if status == 201:
                success_count += 1
            elif status == 409:
                rejected_count += 1
            else:
                rejected_count += 1
                
    total_time = (time.time() - start_time) * 1000
    avg_latency = sum(latencies) / len(latencies) if latencies else 0
    
    print(f"\n[3/3] Kết Quả Kiểm Thử Flash Sale:")
    print("=" * 60)
    print(f"  - Tổng số request đồng thời   : {CONCURRENT_USERS}")
    print(f"  - Số đơn ĐẶT HÀNG THÀNH CÔNG : {success_count} (Kỳ vọng: {INITIAL_STOCK})")
    print(f"  - Số đơn BỊ TỪ CHỐI (Hết hàng): {rejected_count} (Kỳ vọng: {CONCURRENT_USERS - INITIAL_STOCK})")
    print(f"  - Tổng thời gian hoàn thành   : {total_time:.1f} ms")
    print(f"  - Độ trễ trung bình (Latency) : {avg_latency:.1f} ms/req")
    print("=" * 60)
    
    # Query final stock from DB
    try:
        stats_url = f"{GATEWAY_URL}/orders/benchmark/stats?productId={PRODUCT_ID}"
        with urllib.request.urlopen(stats_url) as resp:
            stats = json.loads(resp.read().decode()).get("data", {})
            curr_stock = stats.get("currentStock", 0)
            print(f"  - Tồn kho còn lại trong PostgreSQL : {curr_stock}")
            
            if curr_stock == 0 and success_count == INITIAL_STOCK:
                print("\n  [✓] ĐÁNH GIÁ: TUYỆT VỜI! 100% ĐẠT CHUẨN!")
                print("      Redis Distributed Lock hoạt động hoàn hảo: Không bị ÂM KHO (Zero Overselling).")
            else:
                print(f"\n  [!] Chênh lệch: Thành công={success_count}, Kho còn={curr_stock}")
    except Exception as e:
        print(f"  [!] Không lấy được stats: {e}")

if __name__ == "__main__":
    run_benchmark()
