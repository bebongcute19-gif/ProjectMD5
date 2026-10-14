import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Danh dau 200 (Login/Inventory), 201 (Tao don thanh cong) va 409 (Tu choi hop le khi het hang) la Expected Status
// Giup chi so http_req_failed hien thi chinh xac 0.00% tren bao cao hoi dong
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

// Cac chi so do luong tuy chinh cho Bao cao Bao ve Do an
const successfulOrders = new Counter('successful_orders');
const soldOutRejections = new Counter('sold_out_rejections');
const rateLimitHits = new Counter('rate_limit_hits');
const orderDuration = new Trend('order_duration_ms');
const successOrderDuration = new Trend('success_order_duration_ms');

export const options = {
  scenarios: {
    // Kich ban: Bung no tai Flash Sale dong thoi cuc dai (500 - 1000 req/s)
    flash_sale_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      stages: [
        { target: 500, duration: '10s' },  // Khoi dong tang dan len 500 RPS
        { target: 1000, duration: '20s' }, // Gio cao diem Flash Sale dat dinh 1000 RPS
        { target: 500, duration: '10s' },  // Ha nhiet dan ve 500 RPS
        { target: 0, duration: '5s' },     // Ket thuc
      ],
    },
  },
  thresholds: {
    // Tieu chuan cam ket chat luong (Quality Gates) khi bao cao voi Hoi dong
    'http_req_duration': ['p(95)<1500', 'p(99)<3000'], // 95% request phan hoi duoi 1.5s ngay ca khi tai 1000 RPS
    'rate_limit_hits': ['count==0'],                  // Khong co request nao bi chan nham boi Rate Limiting
    'successful_orders': ['count==100'],              // Dung chinh xac 100 don thanh cong (khop tuyet doi voi 100 ton kho da nap)
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

// Ham thiet lap ban dau (Setup): Dang nhap tai khoan & Dat lai ton kho truoc khi test tai
export function setup() {
  console.log('>>> [k6 Setup] Dang dang nhap tai khoan Customer & Admin...');

  // 1. Dang nhap Khach hang
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: 'customer@ecommerce.com', password: 'Customer@123' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  let token = '';
  if (loginRes.status === 200) {
    token = loginRes.json('data.token');
    console.log('>>> [k6 Setup] Lay ma JWT Token Customer thanh cong.');
  } else {
    console.error(`Dang nhap that bai: ${loginRes.body}`);
  }

  // 2. Dang nhap Admin & Dat lai ton kho San pham #1 ve 100 cai de chay test tai
  const adminLoginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: 'admin@ecommerce.com', password: 'Admin@123' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (adminLoginRes.status === 200) {
    const adminToken = adminLoginRes.json('data.token');
    http.post(
      `${BASE_URL}/api/v1/orders/benchmark/reset-stock?productId=1&stock=100`,
      null,
      { headers: { Authorization: `Bearer ${adminToken}` } }
    );
    console.log('>>> [k6 Setup] Da khoi tao lai kho San pham #1 thanh dung 100 cai.');
  }

  return { token };
}

export default function (data) {
  const payload = JSON.stringify({
    items: [
      { productId: 1, quantity: 1 }
    ],
    notes: 'Yeu cau kiem thu tai tu k6'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.token}`,
      'X-Forwarded-For': `10.0.0.${Math.floor(Math.random() * 254) + 1}`, // Gia lap dai IP khac nhau tu nhieu client dong thoi
    },
    timeout: '5s',
  };

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, params);
  orderDuration.add(res.timings.duration);

  if (res.status === 201) {
    successfulOrders.add(1);
    successOrderDuration.add(res.timings.duration);
    check(res, { 'Tao don hang thanh cong (201 Created)': (r) => r.status === 201 });
  } else if (res.status === 409) {
    soldOutRejections.add(1);
    check(res, { 'Tu choi an toan khi het hang (409 Conflict)': (r) => r.status === 409 });
  } else if (res.status === 400) {
    check(res, { 'Loi Bad Request khong mong muon (400)': (r) => false });
  } else if (res.status === 429) {
    rateLimitHits.add(1);
    console.warn('Da cham gioi han Rate Limit (Ma 429)! Can tang them gia tri RATE_LIMIT_MAX.');
  } else {
    check(res, { 'Trang thai ngoai du kien': (r) => false });
  }
}

// Ham don dep sau bai test (Teardown): Kiem tra ton kho cuoi cung trong co so du lieu
export function teardown(data) {
  console.log('>>> [k6 Teardown] Bai test tai ket thuc! Dang kiem tra ton kho thuc te trong DB...');
  const invRes = http.get(`${BASE_URL}/api/v1/orders/inventory/1`);
  if (invRes.status === 200) {
    const available = invRes.json('availableStock');
    const reserved = invRes.json('reservedStock');
    console.log(`>>> [Ton kho San pham #1 sau cung]: Kha dung = ${available}, Dang giu = ${reserved}`);
    if (available < 0) {
      console.error(`>>> CANH BAO: Phat hien am kho (Oversold)! Ton kho = ${available}`);
    } else if (available === 0) {
      console.log('>>> BAO TOAN KHO THANH CONG: Dung 100 don mua thanh cong, ton kho ve 0 va khong ghi nhan oversell.');
    }
  }
}
