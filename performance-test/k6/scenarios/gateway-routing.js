import http from 'k6/http';
import { sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import { recordMetrics, resourceLatency, authLatency } from '../helpers/metrics.js';
import { buildErrorReport } from '../helpers/error-logger.js';
import { env } from '../config/environment.js';
import { LOAD, SMOKE } from '../config/options.js';

/**
 * gateway-routing.js — Load test toàn bộ luồng routing của API Gateway.
 *
 * Kịch bản:
 *   1. setup()   → login admin + user 1 lần, lưu tokens vào shared data
 *   2. default() → mỗi VU thực hiện mixed request pattern:
 *      - GET /api/resources/products (40%) — protected, cần products:READ
 *      - GET /api/resources/orders   (30%) — protected, cần orders:READ
 *      - GET /api/resources/profile/whoami (20%) — identity injection test
 *      - POST /api/auth/refresh     (10%) — kiểm tra token refresh path
 *
 * Điều chỉnh TEST_PROFILE để chọn loại test:
 *   SMOKE, LOAD (default), STRESS, SOAK, SPIKE, BREAKPOINT
 */

// ── Chọn profile test ────────────────────────────────────────────────────────
const TEST_PROFILE = __ENV.TEST_PROFILE || 'LOAD';
const PROFILES = { SMOKE, LOAD };

export const options = PROFILES[TEST_PROFILE] || LOAD;

// ── Setup: login 1 lần, share tokens ────────────────────────────────────────
export function setup() {
  // register trước (bỏ qua nếu đã tồn tại), rồi login
  const adminTokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  const userTokens  = ensureAndLogin(env.userUsername,  env.userPassword,  env.userRoles);
  return { adminTokens, userTokens };
}

// ── Main loop ────────────────────────────────────────────────────────────────
export default function (data) {
  const { userTokens, adminTokens } = data;
  const roll = Math.random();

  if (roll < 0.40) {
    // GET /api/resources/products
    const res = http.get(env.productsUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'GET /api/resources/products' },
    });
    recordMetrics(res, resourceLatency, 'products', [200]);

  } else if (roll < 0.70) {
    // GET /api/resources/orders
    const res = http.get(env.ordersUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'GET /api/resources/orders' },
    });
    recordMetrics(res, resourceLatency, 'orders', [200]);

  } else if (roll < 0.90) {
    // GET /api/resources/profile/k6user — kiểm tra header injection
    const res = http.get(env.profileUrl, {
      headers: authHeaders(adminTokens.accessToken),
      tags:    { name: 'GET /api/resources/profile/k6user' },
    });
    recordMetrics(res, resourceLatency, 'whoami', [200]);

  } else {
    // POST /api/auth/refresh
    const res = http.post(
      env.refreshUrl,
      JSON.stringify({ refreshToken: userTokens.refreshToken }),
      {
        headers: { 'Content-Type': 'application/json' },
        tags:    { name: 'POST /api/auth/refresh' },
      },
    );
    const body = JSON.parse(res.body);
    userTokens.refreshToken = body.refreshToken
    recordMetrics(res, authLatency, 'refresh', [200]);
  }

  sleep(Math.random() * 0.5 + 0.1); // 100ms – 600ms think time
}

// ── Teardown ─────────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log('Gateway routing test hoàn thành.');
}

export function handleSummary(data) {
  return {
    'results/gateway-routing-errors.json': buildErrorReport('gateway-routing'),
  };
}
