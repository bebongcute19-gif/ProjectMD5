/**
 * NEXUS TECH - Omnichannel Microservices Frontend Application
 * Handles Storefront, Concurrency Benchmark, Saga Interactions, and Service Monitoring
 */

// Auto-detect API Base: If accessed via Nginx (port 80/3000), use '/api/v1', else fallback to 'http://localhost:8080/api/v1'
const API_BASE = window.location.port === '8080' || window.location.port === '' 
  ? '/api/v1' 
  : `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;

let state = {
  products: [],
  cart: [],
  currentUser: {
    id: 1,
    email: 'customer@ecommerce.com',
    fullName: 'Nguyễn Văn A (Khách Mua)',
    role: 'CUSTOMER'
  },
  jwtToken: localStorage.getItem('nexus_jwt') || '',
  selectedConcurrency: 50,
  benchmarkProductId: 1,
  heroProduct: null
};

// Common Authentication Header Helper for Protected API Gateway Routes
function authHeaders(extraHeaders = {}) {
  const headers = { 'Content-Type': 'application/json', ...extraHeaders };
  const token = state.jwtToken || localStorage.getItem('nexus_jwt');
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

function updateAdminVisibility() {
  const isAdmin = state.currentUser && state.currentUser.role === 'ADMIN';
  document.querySelectorAll('.admin-only').forEach(el => {
    el.style.display = isAdmin ? '' : 'none';
  });
}

async function autoLoginDefaultCustomer() {
  try {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'customer@ecommerce.com', password: 'Customer@123' })
    });
    const json = await res.json();
    if (res.ok && json.success) {
      state.jwtToken = json.data.token;
      state.currentUser = json.data.user;
      localStorage.setItem('nexus_jwt', json.data.token);
      localStorage.setItem('nexus_user', JSON.stringify(json.data.user));
      const nameEl = document.getElementById('user-name-display');
      const avatarEl = document.getElementById('user-avatar-initial');
      if (nameEl) nameEl.innerText = json.data.user.fullName;
      if (avatarEl) avatarEl.innerText = json.data.user.fullName[0];
    }
  } catch (_) {}
}

// ============================================================================
// Initialization & Navigation
// ============================================================================
document.addEventListener('DOMContentLoaded', async () => {
  initCountdownTimer();

  // Restore saved session if available
  const savedUser = localStorage.getItem('nexus_user');
  if (savedUser) {
    try {
      state.currentUser = JSON.parse(savedUser);
      const nameEl = document.getElementById('user-name-display');
      const avatarEl = document.getElementById('user-avatar-initial');
      if (nameEl) nameEl.innerText = state.currentUser.fullName;
      if (avatarEl) avatarEl.innerText = state.currentUser.fullName[0];
    } catch (_) {}
  } else if (!state.jwtToken) {
    await autoLoginDefaultCustomer();
  }

  updateAdminVisibility();
  fetchProducts();
  fetchHeroStock();
  loadOrderHistory();
  loadNotifications();
  checkAllServicesHealth();

  // Poll for live stock across all products and notifications
  setInterval(refreshAllInventories, 3000);
  setInterval(loadNotifications, 7000);
});

function switchTab(tabId) {
  document.querySelectorAll('.tab-pane').forEach(el => el.classList.remove('active'));
  document.querySelectorAll('.nav-link').forEach(el => el.classList.remove('active'));

  const targetPane = document.getElementById(`tab-${tabId}`);
  if (targetPane) targetPane.classList.add('active');

  // Highlight active link
  const links = document.querySelectorAll('.nav-link');
  if (tabId === 'storefront') links[0]?.classList.add('active');
  if (tabId === 'concurrency-lab') {
    links[1]?.classList.add('active');
    populateBenchmarkProductSelect();
  }
  if (tabId === 'saga-monitor') links[2]?.classList.add('active');
  if (tabId === 'orders-tab') { links[3]?.classList.add('active'); loadOrderHistory(); }
  if (tabId === 'notifications-tab') { links[4]?.classList.add('active'); loadNotifications(); }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️'}</span> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}

// ============================================================================
// Storefront & Catalog
// ============================================================================
async function fetchProducts() {
  try {
    const res = await fetch(`${API_BASE}/products`);
    const json = await res.json();
    if (json.success && Array.isArray(json.data)) {
      state.products = json.data;

      // Update real available stock from Order Service
      await Promise.all(state.products.map(async (p) => {
        try {
          const invRes = await fetch(`${API_BASE}/orders/inventory/${p.id}`);
          if (invRes.ok) {
            const invJson = await invRes.json();
            if (invJson.success && invJson.availableStock !== undefined) {
              p.stock = invJson.availableStock;
            }
          }
        } catch (_) {}
      }));

      renderProductsGrid(state.products);

      // Find hero product #1
      state.heroProduct = state.products.find(p => p.id === 1) || state.products[0];
      
      // Populate benchmark product dropdown with all platform products
      populateBenchmarkProductSelect();
    }
  } catch (err) {
    console.error('Lỗi tải sản phẩm:', err);
    showToast('Chưa thể kết nối tới Product Service qua API Gateway', 'error');
  }
}

function renderProductsGrid(items) {
  const grid = document.getElementById('products-grid');
  if (!grid) return;

  if (items.length === 0) {
    grid.innerHTML = '<p style="color: var(--text-muted); grid-column: 1/-1; text-align: center;">Không tìm thấy sản phẩm nào.</p>';
    return;
  }

  grid.innerHTML = items.map(p => `
    <div class="product-card" id="product-card-${p.id}">
      <div class="product-img-wrap">
        <img class="product-img" src="${p.imageUrl || 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e'}" alt="${p.name}">
        <span class="product-tag ${p.flashSale ? 'tag-flash' : 'tag-normal'}">
          ${p.flashSale ? '⚡ Flash Sale' : p.categorySlug || 'Chính hãng'}
        </span>
      </div>
      <div class="product-body">
        <h4 class="product-name" title="${p.name}">${p.name}</h4>
        <p class="product-desc">${p.description || ''}</p>
        <div class="product-footer">
          <div class="product-price-box">
            <span class="card-price ${p.flashSale ? 'flash' : ''}">
              ${formatCurrency(p.flashSale && p.flashSalePrice ? p.flashSalePrice : p.price)}
            </span>
            <span class="card-stock">Kho thật: <strong id="product-stock-${p.id}" style="color: ${p.stock <= 0 ? 'var(--danger)' : 'var(--success)'};">${p.stock}</strong> cái</span>
          </div>
          <div style="display: flex; gap: 0.4rem;">
            <button class="btn btn-secondary btn-sm" onclick="addToCart(${p.id})">🛒</button>
            <button id="product-btn-${p.id}" class="btn ${p.flashSale ? 'btn-flash' : 'btn-primary'} btn-sm" onclick="quickBuy(${p.id})" ${p.stock <= 0 ? 'disabled' : ''}>
              ${p.stock <= 0 ? 'Hết hàng' : 'Mua'}
            </button>
          </div>
        </div>
      </div>
    </div>
  `).join('');
}

function filterCatalog(category, btn) {
  document.querySelectorAll('.chip-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');

  if (category === 'all') {
    renderProductsGrid(state.products);
  } else if (category === 'flash') {
    renderProductsGrid(state.products.filter(p => p.flashSale));
  } else {
    renderProductsGrid(state.products.filter(p => p.categorySlug === category));
  }
}

function handleSearch(keyword) {
  const kw = keyword.toLowerCase().trim();
  const filtered = state.products.filter(p => 
    p.name.toLowerCase().includes(kw) || 
    (p.description && p.description.toLowerCase().includes(kw))
  );
  renderProductsGrid(filtered);
}

function formatCurrency(amount) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

// ============================================================================
// Real-time Stock Sync & Management (Dual UI: Hero Banner + Product Cards Grid)
// ============================================================================
function updateProductStockUI(productId, newStock) {
  const stockVal = Math.max(0, parseInt(newStock, 10) || 0);

  // 1. Update in local state array
  const product = state.products.find(p => p.id === productId);
  if (product) {
    product.stock = stockVal;
  }

  // 2. Synchronize product card in the catalog grid below
  const stockStrong = document.getElementById(`product-stock-${productId}`);
  if (stockStrong) {
    stockStrong.innerText = stockVal;
    stockStrong.style.color = stockVal <= 0 ? 'var(--danger)' : 'var(--success)';
  }

  // 3. Synchronize purchase button state
  const buyBtn = document.getElementById(`product-btn-${productId}`);
  if (buyBtn) {
    if (stockVal <= 0) {
      buyBtn.setAttribute('disabled', 'disabled');
      buyBtn.innerText = 'Hết hàng';
    } else {
      buyBtn.removeAttribute('disabled');
      buyBtn.innerText = 'Mua';
    }
  }

  // 4. Synchronize Flash Sale Hero banner if Product #1
  if (productId === 1) {
    const initialStock = 10;
    const stockEl = document.getElementById('hero-stock-count');
    const barEl = document.getElementById('hero-stock-bar');

    if (stockEl) stockEl.innerText = `${stockVal} / ${initialStock} cái`;
    if (barEl) {
      const percent = Math.max(0, Math.min(100, (stockVal / initialStock) * 100));
      barEl.style.width = `${percent}%`;
    }
  }

  // 5. Đồng bộ ô số liệu "Tồn Kho Còn Lại Trong DB" nếu đây là sản phẩm đang được chọn test benchmark
  const currentBenchmarkId = state.benchmarkProductId || 1;
  if (productId === currentBenchmarkId) {
    const statRemaining = document.getElementById('stat-remaining-stock');
    if (statRemaining) statRemaining.innerText = stockVal;
  }
}

async function refreshAllInventories() {
  if (!state.products || state.products.length === 0) return;
  await Promise.all(state.products.map(async (p) => {
    try {
      const res = await fetch(`${API_BASE}/orders/inventory/${p.id}`);
      if (res.ok) {
        const json = await res.json();
        if (json.success && json.availableStock !== undefined) {
          updateProductStockUI(p.id, json.availableStock);
        }
      }
    } catch (_) {}
  }));
}

async function fetchHeroStock() {
  try {
    const res = await fetch(`${API_BASE}/orders/inventory/1`);
    const json = await res.json();
    if (json.success && json.availableStock !== undefined) {
      updateProductStockUI(1, json.availableStock);
    }
  } catch (err) {
    // silently fail
  }
}

async function buyHeroFlashSale() {
  await quickBuy(1);
}

async function quickBuy(productId) {
  const product = state.products.find(p => p.id === productId) || {
    id: productId,
    name: 'iPhone 16 Pro Max 256GB Desert Titanium',
    price: 19990000,
    sku: 'IPHONE-16-PM-256'
  };

  const price = product.flashSale && product.flashSalePrice ? product.flashSalePrice : product.price;

  const orderPayload = {
    customerId: state.currentUser.id,
    customerEmail: state.currentUser.email,
    customerName: state.currentUser.fullName,
    shippingAddress: '77 Nguyễn Huệ, Quận 1, TP.HCM',
    phone: '0987654321',
    paymentMethod: 'VNPAY',
    items: [
      {
        productId: product.id,
        productName: product.name,
        sku: product.sku || `SKU-${product.id}`,
        price: price,
        quantity: 1
      }
    ]
  };

  try {
    showToast('Đang gửi yêu cầu mua với Redis Distributed Lock...', 'info');
    const res = await fetch(`${API_BASE}/orders`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(orderPayload)
    });

    const json = await res.json();

    if (res.ok && json.success) {
      showToast(`🎉 Đặt hàng thành công! Mã đơn: ${json.data.orderNumber}`, 'success');
      refreshAllInventories();
      loadOrderHistory();
      loadNotifications();
    } else {
      showToast(`⚠️ Không thể đặt hàng: ${json.message}`, 'error');
    }
  } catch (err) {
    showToast(`Lỗi kết nối tới hệ thống: ${err.message}`, 'error');
  }
}

// ============================================================================
// CONCURRENCY BENCHMARK & RACE CONDITION LAB
// ============================================================================
function setConcurrency(num, btn) {
  state.selectedConcurrency = num;
  document.querySelectorAll('.toggle-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  appendConsoleLog(`[CẤU HÌNH] Đã thiết lập số luồng đồng thời: ${num} luồng.`, 'info');
}

function appendConsoleLog(message, type = 'info') {
  const terminal = document.getElementById('benchmark-terminal');
  if (!terminal) return;
  const line = document.createElement('div');
  line.className = `console-line ${type}`;
  line.innerText = `[${new Date().toLocaleTimeString()}] ${message}`;
  terminal.appendChild(line);
  terminal.scrollTop = terminal.scrollHeight;
}

function clearConsoleLog() {
  const terminal = document.getElementById('benchmark-terminal');
  if (terminal) terminal.innerHTML = '';
}

// ============================================================================
// DYNAMIC BENCHMARK PRODUCT SELECTOR & LOGIC
// ============================================================================
function populateBenchmarkProductSelect() {
  const select = document.getElementById('benchmark-product-select');
  if (!select) return;

  if (!state.products || state.products.length === 0) {
    // Nếu chưa có dữ liệu sản phẩm, gọi fetchProducts và không xóa danh sách mặc định sẵn có trong HTML
    fetchProducts();
    return;
  }

  const currentVal = state.benchmarkProductId || parseInt(select.value, 10) || 1;

  select.innerHTML = state.products.map(p => {
    const priceFormatted = formatCurrency(p.flashSale && p.flashSalePrice ? p.flashSalePrice : p.price);
    const isSelected = p.id === currentVal ? 'selected' : '';
    const stockDisplay = p.stock !== undefined ? p.stock : 0;
    return `<option value="${p.id}" ${isSelected}>#${p.id} - ${p.name} (Tồn kho: ${stockDisplay} cái | ${priceFormatted})</option>`;
  }).join('');

  select.value = currentVal;
  updateBenchmarkProductDetails(currentVal);
}

async function handleBenchmarkProductChange(productIdStr) {
  const productId = parseInt(productIdStr, 10) || 1;
  state.benchmarkProductId = productId;

  // Reset ket qua kiem thu cu de chuan bi cho luot test san pham moi
  const statSuccess = document.getElementById('stat-successful-orders');
  if (statSuccess) statSuccess.innerText = '0';
  const statReject = document.getElementById('stat-rejected-orders');
  if (statReject) statReject.innerText = '0';
  const statOversold = document.getElementById('stat-oversold-count');
  if (statOversold) statOversold.innerText = '0';

  // Lay ngay ton kho that moi nhat tu DB qua Order Service
  try {
    const res = await fetch(`${API_BASE}/orders/inventory/${productId}`);
    if (res.ok) {
      const json = await res.json();
      if (json.success && json.availableStock !== undefined) {
        const p = state.products.find(x => x.id === productId);
        if (p) p.stock = json.availableStock;
      }
    }
  } catch (_) {}

  updateBenchmarkProductDetails(productId);
  const p = state.products.find(x => x.id === productId);
  appendConsoleLog(`[CHỌN SẢN PHẨM] Đã chọn Sản phẩm #${productId} (${p ? p.name : ''}). Tồn kho hiện tại: ${p?.stock ?? 0} cái.`, 'info');
}

async function selectRandomBenchmarkProduct() {
  if (!state.products || state.products.length === 0) {
    showToast('Danh sách sản phẩm chưa tải xong, vui lòng đợi!', 'warn');
    return;
  }
  const randomIndex = Math.floor(Math.random() * state.products.length);
  const randomProduct = state.products[randomIndex];

  state.benchmarkProductId = randomProduct.id;
  const select = document.getElementById('benchmark-product-select');
  if (select) {
    select.value = randomProduct.id;
  }

  // Reset ket qua kiem thu cu
  const statSuccess = document.getElementById('stat-successful-orders');
  if (statSuccess) statSuccess.innerText = '0';
  const statReject = document.getElementById('stat-rejected-orders');
  if (statReject) statReject.innerText = '0';
  const statOversold = document.getElementById('stat-oversold-count');
  if (statOversold) statOversold.innerText = '0';

  // Lay ngay ton kho that tu DB cho san pham nay
  try {
    const res = await fetch(`${API_BASE}/orders/inventory/${randomProduct.id}`);
    if (res.ok) {
      const json = await res.json();
      if (json.success && json.availableStock !== undefined) {
        randomProduct.stock = json.availableStock;
      }
    }
  } catch (_) {}

  updateBenchmarkProductDetails(randomProduct.id);
  appendConsoleLog(`🎲 [CHỌN NGẪU NHIÊN] Đã chọn: #${randomProduct.id} - ${randomProduct.name} (Tồn kho: ${randomProduct.stock ?? 0} cái)`, 'warn');
  showToast(`🎲 Đã chọn ngẫu nhiên: #${randomProduct.id} - ${randomProduct.name}`, 'info');
}

function updateBenchmarkProductDetails(productId) {
  const product = state.products.find(p => p.id === productId) || state.products[0];
  if (!product) return;

  const stockVal = product.stock !== undefined ? product.stock : 0;

  // 1. Cập nhật thẻ preview chi tiết phía dưới dropdown
  const preview = document.getElementById('benchmark-selected-product-info');
  if (preview) {
    const priceStr = formatCurrency(product.flashSale && product.flashSalePrice ? product.flashSalePrice : product.price);
    preview.innerHTML = `
      <span>Tồn kho hiện tại: <strong style="color: ${stockVal <= 0 ? 'var(--danger)' : 'var(--success)'};">${stockVal} cái</strong></span>
      <span>Giá test: <strong style="color: var(--accent-primary);">${priceStr}</strong></span>
      <span>Mã SKU: <code>${product.sku || 'SKU-' + product.id}</code></span>
    `;
  }

  // 2. Cập nhật nút Reset Kho hiển thị ID sản phẩm đang chọn
  const resetBtn = document.getElementById('btn-reset-benchmark-stock');
  if (resetBtn) {
    resetBtn.innerHTML = `🔄 Reset Kho #${productId} về 10 Cái`;
  }

  // 3. Cập nhật giải thích cơ chế khóa phân tán với ID sản phẩm đang chọn
  const lockHint = document.getElementById('benchmark-lock-hint');
  if (lockHint) {
    lockHint.innerHTML = `🛡️ <strong>Cơ chế bảo vệ:</strong> Mỗi request gửi đến Order Service sẽ cố gắng acquire <code>SETNX lock:inventory:${productId} [UUID] PX 5000</code>. Chỉ có thread giữ Lock mới được trừ tồn kho trong DB bằng <code>SELECT ... FOR UPDATE</code>.`;
  }

  // 4. Cập nhật thẻ số liệu "Tồn Kho DB Còn Lại" & tiêu đề kết quả kiểm thử theo đúng sản phẩm đang chọn
  const statRemaining = document.getElementById('stat-remaining-stock');
  if (statRemaining) {
    statRemaining.innerText = stockVal;
  }
  const statRemainingTitle = document.getElementById('stat-remaining-title');
  if (statRemainingTitle) {
    statRemainingTitle.innerText = `Tồn kho #${productId} còn lại trong DB`;
  }
  const statSuccessTitle = document.getElementById('stat-successful-title');
  if (statSuccessTitle) {
    statSuccessTitle.innerText = `Đơn hàng thành công (Tồn kho: ${stockVal})`;
  }
}

async function resetInventoryStock() {
  const productId = state.benchmarkProductId || 1;
  const product = state.products.find(p => p.id === productId);
  const productName = product ? product.name : `Sản phẩm #${productId}`;

  try {
    appendConsoleLog(`Đang reset tồn kho sản phẩm #${productId} (${productName}) về 10 cái qua Order Service...`, 'warn');
    const res = await fetch(`${API_BASE}/orders/benchmark/reset-stock?productId=${productId}&stock=10`, {
      method: 'POST',
      headers: authHeaders()
    });
    const json = await res.json();
    if (json.success) {
      showToast(`Đã reset kho sản phẩm #${productId} về 10 cái!`, 'success');
      appendConsoleLog(`✅ Reset kho #${productId} hoàn tất! Tồn kho hiện tại: 10 cái.`, 'info');
      
      document.getElementById('stat-successful-orders').innerText = '0';
      document.getElementById('stat-rejected-orders').innerText = '0';
      document.getElementById('stat-remaining-stock').innerText = '10';
      document.getElementById('stat-oversold-count').innerText = '0';
      
      // Đồng bộ vào state cục bộ và cập nhật lại giao diện
      if (product) product.stock = 10;
      updateBenchmarkProductDetails(productId);
      populateBenchmarkProductSelect();
      refreshAllInventories();
    } else {
      showToast('Không thể reset kho: ' + (json.message || 'Lỗi server'), 'error');
    }
  } catch (err) {
    showToast('Lỗi reset tồn kho: ' + err.message, 'error');
  }
}

async function runLiveConcurrencyBenchmark() {
  const count = state.selectedConcurrency;
  const productId = state.benchmarkProductId || 1;
  const selectedProduct = state.products.find(p => p.id === productId) || {
    id: productId,
    name: 'Sản phẩm kiểm thử #' + productId,
    sku: 'SKU-' + productId,
    price: 19990000
  };

  const testPrice = selectedProduct.flashSale && selectedProduct.flashSalePrice 
    ? selectedProduct.flashSalePrice 
    : selectedProduct.price;

  const runBtn = document.getElementById('run-benchmark-btn');
  if (runBtn) runBtn.disabled = true;

  appendConsoleLog(`🚀 BẮT ĐẦU BENCHMARK: Bắn đồng thời ${count} request mua sản phẩm #${productId} (${selectedProduct.name})...`, 'warn');
  const startTime = performance.now();

  const requests = [];
  for (let i = 1; i <= count; i++) {
    const payload = {
      customerId: 1000 + i,
      customerEmail: `tester_${i}@benchmark.local`,
      customerName: `Tester Luồng #${i}`,
      shippingAddress: 'Lab Test Server',
      phone: `0900000${String(i).padStart(3, '0')}`,
      paymentMethod: 'VNPAY',
      items: [
        {
          productId: selectedProduct.id,
          productName: selectedProduct.name,
          sku: selectedProduct.sku || `SKU-${selectedProduct.id}`,
          price: testPrice,
          quantity: 1
        }
      ]
    };

    requests.push(
      fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(payload)
      }).then(async res => ({
        status: res.status,
        body: await res.json().catch(() => ({}))
      })).catch(err => ({ status: 500, body: { message: err.message } }))
    );
  }

  // Await all concurrent executions
  const results = await Promise.all(requests);
  const duration = (performance.now() - startTime).toFixed(1);

  let successes = 0;
  let rejects = 0;

  results.forEach((r, idx) => {
    if (r.status === 201) {
      successes++;
      appendConsoleLog(`Thread #${idx + 1}: ✅ THÀNH CÔNG giữ hàng! Đơn #${r.body.data?.orderNumber}`, 'info');
    } else {
      rejects++;
      if (rejects <= 5 || rejects === count - 10) {
        appendConsoleLog(`Thread #${idx + 1}: ❌ TỪ CHỐI (Hết hàng): ${r.body?.message || 'Conflict'}`, 'err');
      }
    }
  });

  appendConsoleLog(`🏁 HOÀN TẤT BENCHMARK trong ${duration}ms!`, 'warn');
  appendConsoleLog(`📊 Tổng gửi: ${count} | Thành công: ${successes} | Bị từ chối: ${rejects}`, 'info');

  // Verify final database stock for this product
  const statsRes = await fetch(`${API_BASE}/orders/benchmark/stats?productId=${productId}`, {
    headers: authHeaders()
  }).then(r => r.json()).catch(() => ({}));
  const finalStock = statsRes.data?.currentStock ?? 0;

  document.getElementById('stat-successful-orders').innerText = successes;
  document.getElementById('stat-rejected-orders').innerText = rejects;
  document.getElementById('stat-remaining-stock').innerText = finalStock;
  
  const oversold = Math.max(0, -finalStock);
  const oversoldEl = document.getElementById('stat-oversold-count');
  oversoldEl.innerText = oversold;

  if (oversold === 0 && finalStock >= 0) {
    appendConsoleLog(`🛡️ KẾT QUẢ ĐẠT CHUẨN: KHÔNG CÓ RACE CONDITION (ÂM KHO = 0)! Sản phẩm #${productId} bảo vệ kho thành công!`, 'info');
    showToast(`Benchmark thành công: ${successes} mua được, ${rejects} từ chối. Kho #${productId} = ${finalStock}`, 'success');
  } else {
    appendConsoleLog(`⚠️ PHÁT HIỆN LỖI: Kho #${productId} bị âm!`, 'err');
  }

  // Cập nhật tồn kho sản phẩm trong state và dropdown
  const curP = state.products.find(p => p.id === productId);
  if (curP) curP.stock = finalStock;
  updateBenchmarkProductDetails(productId);
  populateBenchmarkProductSelect();

  if (runBtn) runBtn.disabled = false;
  refreshAllInventories();
  loadOrderHistory();
  loadNotifications();
}

// ============================================================================
// SAGA TRANSACTIONS & ORDER HISTORY
// ============================================================================
async function loadOrderHistory() {
  const tbody = document.getElementById('orders-table-body');
  if (!tbody) return;

  const token = state.jwtToken || localStorage.getItem('nexus_jwt');
  if (!token) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Vui lòng <a href="javascript:void(0)" onclick="toggleAuthModal()" style="color: var(--accent-primary);">đăng nhập</a> để xem danh sách đơn hàng.</td></tr>';
    return;
  }

  try {
    const isAdmin = state.currentUser && state.currentUser.role === 'ADMIN';
    const endpoint = isAdmin ? `${API_BASE}/orders/admin/all` : `${API_BASE}/orders`;
    const res = await fetch(endpoint, { headers: authHeaders() });
    const json = await res.json();

    if (json.success && Array.isArray(json.data) && json.data.length > 0) {
      tbody.innerHTML = json.data.slice(0, 25).map(o => `
        <tr>
          <td><strong>${o.orderNumber}</strong></td>
          <td>${o.customerName}<br><span style="font-size: 0.75rem; color: var(--text-faint);">${o.customerEmail}</span></td>
          <td>${o.items?.map(i => `${i.productName} (x${i.quantity})`).join(', ') || 'N/A'}</td>
          <td><strong>${formatCurrency(o.totalAmount)}</strong></td>
          <td>
            <span class="status-pill ${o.status.toLowerCase()}">${o.status}</span>
          </td>
          <td style="font-size: 0.75rem; color: var(--text-faint); font-family: var(--font-mono);">
            ${new Date(o.createdAt).toLocaleTimeString()}
          </td>
          <td>
            ${o.status === 'PENDING' ? `
              <div style="display: flex; gap: 0.3rem;">
                <button class="btn btn-success btn-sm" onclick="simulatePayment(${o.id}, '${o.orderNumber}', 'SUCCESS')">
                  ✅ Thanh toán
                </button>
                <button class="btn btn-danger btn-sm" onclick="simulatePayment(${o.id}, '${o.orderNumber}', 'FAIL')">
                  ❌ Hủy / Bồi hoàn Saga
                </button>
              </div>
            ` : `<span style="font-size: 0.8rem; color: var(--text-faint);">Hoàn tất luồng Saga</span>`}
          </td>
        </tr>
      `).join('');
    } else {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Chưa có đơn hàng nào.</td></tr>';
    }
  } catch (err) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--danger);">Không thể kết nối đến Order Service</td></tr>';
  }
}

async function simulatePayment(orderId, orderNumber, action) {
  try {
    showToast(`Đang mô phỏng giao dịch tại cổng Payment (${action})...`, 'info');
    const res = await fetch(`${API_BASE}/payments/process`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        orderId: orderId,
        orderNumber: orderNumber,
        action: action
      })
    });

    const json = await res.json();
    if (res.ok && json.success) {
      if (action === 'SUCCESS') {
        showToast('Thanh toán thành công! Sự kiện gửi qua RabbitMQ.', 'success');
      } else {
        showToast('Thanh toán thất bại! Saga bù trừ tự động hoàn lại tồn kho!', 'error');
      }
    } else {
      showToast(`Lỗi thanh toán: ${json.message || 'Không thể xử lý'}`, 'error');
    }

    setTimeout(() => {
      loadOrderHistory();
      refreshAllInventories();
      loadNotifications();
    }, 1000);

  } catch (err) {
    showToast('Lỗi xử lý thanh toán: ' + err.message, 'error');
  }
}

// ============================================================================
// NOTIFICATIONS STREAM
// ============================================================================
async function loadNotifications() {
  const feed = document.getElementById('notifications-feed-list');
  const countBadge = document.getElementById('notif-count-badge');
  if (!feed) return;

  try {
    const res = await fetch(`${API_BASE}/notifications`, {
      headers: authHeaders()
    });
    const json = await res.json();

    if (json.success && Array.isArray(json.data)) {
      if (countBadge) countBadge.innerText = json.data.length;

      if (json.data.length === 0) {
        feed.innerHTML = '<p style="color: var(--text-muted); padding: 1.5rem; text-align: center;">Chưa có thông báo nào từ RabbitMQ.</p>';
        return;
      }

      feed.innerHTML = json.data.slice(0, 20).map(n => `
        <div class="notif-item">
          <div class="notif-icon">
            ${n.eventType === 'ORDER_CREATED' ? '📦' : n.eventType === 'PAYMENT_COMPLETED' ? '💳' : '⚠️'}
          </div>
          <div class="notif-content">
            <h5>${n.title}</h5>
            <p>${n.message}</p>
            <div class="notif-time">
              <span>Đến: ${n.recipientEmail}</span> • <span>${new Date(n.createdAt).toLocaleTimeString()}</span>
            </div>
          </div>
        </div>
      `).join('');
    }
  } catch (err) {
    // silently fail
  }
}

// ============================================================================
// HEALTH CHECK MATRIX
// ============================================================================
async function checkAllServicesHealth() {
  const endpoints = [
    { id: 'ping-gateway', url: `${API_BASE}/products/health` },
    { id: 'ping-auth', url: `${API_BASE}/auth/health` },
    { id: 'ping-product', url: `${API_BASE}/products/health` },
    { id: 'ping-order', url: `${API_BASE}/orders/health` },
    { id: 'ping-payment', url: `${API_BASE}/payments/health` },
    { id: 'ping-notification', url: `${API_BASE}/notifications/health` }
  ];

  for (const s of endpoints) {
    const card = document.getElementById(s.id);
    if (!card) continue;
    const dot = card.querySelector('.status-dot');

    try {
      const res = await fetch(s.url, { signal: AbortSignal.timeout(2000) });
      if (res.ok) {
        dot.className = 'status-dot';
      } else {
        dot.className = 'status-dot down';
      }
    } catch (e) {
      dot.className = 'status-dot down';
    }
  }
}

// ============================================================================
// CART & UI MODALS
// ============================================================================
function toggleCartDrawer() {
  document.getElementById('cart-drawer')?.classList.toggle('open');
}

function addToCart(productId) {
  const product = state.products.find(p => p.id === productId);
  if (!product) return;

  const existing = state.cart.find(i => i.id === productId);
  if (existing) {
    existing.quantity++;
  } else {
    state.cart.push({ ...product, quantity: 1 });
  }

  updateCartUI();
  showToast(`Đã thêm ${product.name} vào giỏ hàng`, 'success');
}

function updateCartUI() {
  const countEl = document.getElementById('cart-item-count');
  const itemsContainer = document.getElementById('cart-drawer-items');
  const totalEl = document.getElementById('cart-total-price');

  const totalCount = state.cart.reduce((sum, i) => sum + i.quantity, 0);
  if (countEl) countEl.innerText = totalCount;

  if (state.cart.length === 0) {
    if (itemsContainer) itemsContainer.innerHTML = '<p style="color: var(--text-muted); text-align: center; margin-top: 3rem;">Giỏ hàng của bạn đang trống!</p>';
    if (totalEl) totalEl.innerText = '0₫';
    return;
  }

  let totalAmount = 0;
  if (itemsContainer) {
    itemsContainer.innerHTML = state.cart.map(item => {
      const price = item.flashSale && item.flashSalePrice ? item.flashSalePrice : item.price;
      const subtotal = price * item.quantity;
      totalAmount += subtotal;

      return `
        <div style="display: flex; gap: 0.8rem; margin-bottom: 1rem; padding-bottom: 0.8rem; border-bottom: 1px solid var(--border-subtle);">
          <img src="${item.imageUrl}" style="width: 50px; height: 50px; object-fit: cover; border-radius: var(--radius-sm);">
          <div style="flex: 1;">
            <h5 style="font-size: 0.88rem; font-weight: 700;">${item.name}</h5>
            <div style="font-size: 0.8rem; color: var(--accent-primary);">${formatCurrency(price)} x ${item.quantity}</div>
          </div>
          <button style="background: transparent; border: none; color: var(--danger); cursor: pointer;" onclick="removeFromCart(${item.id})">✕</button>
        </div>
      `;
    }).join('');
  }

  if (totalEl) totalEl.innerText = formatCurrency(totalAmount);
}

function removeFromCart(productId) {
  state.cart = state.cart.filter(i => i.id !== productId);
  updateCartUI();
}

async function checkoutCart() {
  if (state.cart.length === 0) {
    showToast('Giỏ hàng trống!', 'error');
    return;
  }

  const items = state.cart.map(i => ({
    productId: i.id,
    productName: i.name,
    sku: i.sku || `SKU-${i.id}`,
    price: i.flashSale && i.flashSalePrice ? i.flashSalePrice : i.price,
    quantity: i.quantity
  }));

  const payload = {
    customerId: state.currentUser.id,
    customerEmail: state.currentUser.email,
    customerName: state.currentUser.fullName,
    shippingAddress: 'Tòa nhà Bitexco, Q1, TP.HCM',
    phone: '0987654321',
    paymentMethod: 'VNPAY',
    items: items
  };

  try {
    showToast('Đang tiến hành đặt hàng...', 'info');
    const res = await fetch(`${API_BASE}/orders`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });

    const json = await res.json();
    if (res.ok && json.success) {
      showToast(`Đặt hàng thành công! Mã: ${json.data.orderNumber}`, 'success');
      state.cart = [];
      updateCartUI();
      toggleCartDrawer();
      loadOrderHistory();
      refreshAllInventories();
      loadNotifications();
    } else {
      showToast('Lỗi đặt hàng: ' + json.message, 'error');
    }
  } catch (err) {
    showToast('Lỗi kết nối: ' + err.message, 'error');
  }
}

function toggleAuthModal() {
  document.getElementById('auth-modal')?.classList.toggle('open');
}

function fillAdminAccount() {
  document.getElementById('auth-email-input').value = 'admin@ecommerce.com';
  document.getElementById('auth-password-input').value = 'Admin@123';
}

function fillCustomerAccount() {
  document.getElementById('auth-email-input').value = 'customer@ecommerce.com';
  document.getElementById('auth-password-input').value = 'Customer@123';
}

function handleLogout() {
  state.jwtToken = '';
  state.currentUser = null;
  localStorage.removeItem('nexus_jwt');
  localStorage.removeItem('nexus_user');
  document.getElementById('user-name-display').innerText = 'Chưa đăng nhập';
  document.getElementById('user-avatar-initial').innerText = '?';
  updateAdminVisibility();
  loadOrderHistory();
  showToast('Đã đăng xuất thành công', 'info');
  toggleAuthModal();
}

async function handleLoginSubmit() {
  const email = document.getElementById('auth-email-input').value;
  const password = document.getElementById('auth-password-input').value;

  try {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    const json = await res.json();
    if (res.ok && json.success) {
      state.jwtToken = json.data.token;
      state.currentUser = json.data.user;
      localStorage.setItem('nexus_jwt', json.data.token);
      localStorage.setItem('nexus_user', JSON.stringify(json.data.user));

      document.getElementById('user-name-display').innerText = json.data.user.fullName;
      document.getElementById('user-avatar-initial').innerText = json.data.user.fullName[0];
      updateAdminVisibility();
      loadOrderHistory();
      loadNotifications();
      showToast(`Đăng nhập thành công! Vai trò: ${json.data.user.role}`, 'success');
      toggleAuthModal();
    } else {
      showToast('Đăng nhập thất bại: ' + json.message, 'error');
    }
  } catch (err) {
    showToast('Lỗi kết nối Auth Service: ' + err.message, 'error');
  }
}

function initCountdownTimer() {
  let seconds = 2 * 3600 + 45 * 60 + 18;
  setInterval(() => {
    seconds = Math.max(0, seconds - 1);
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    const hEl = document.getElementById('timer-hours');
    const mEl = document.getElementById('timer-mins');
    const sEl = document.getElementById('timer-secs');
    if (hEl) hEl.innerText = String(h).padStart(2, '0');
    if (mEl) mEl.innerText = String(m).padStart(2, '0');
    if (sEl) sEl.innerText = String(s).padStart(2, '0');
  }, 1000);
}
