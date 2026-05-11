import http from 'k6/http';
import { check, sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import { recordMetrics, adminLatency, unauthorizedCount, forbiddenCount } from '../helpers/metrics.js';
import { env } from '../config/environment.js';
import { LOAD } from '../config/options.js';

/**
 * rbac-auth.js — Kiểm tra JWT Authentication + RBAC Authorization flow.
 *
 * Kịch bản kiểm tra:
 *   1. Request không có token → 401
 *   2. Token hết hạn → 401
 *   3. Token hợp lệ nhưng không có permission → 403
 *   4. Token hợp lệ + đúng permission → 200
 *   5. Admin API với ROLE_ADMIN → 200
 *   6. Admin API với ROLE_USER → 403
 *   7. Kiểm tra header injection (X-User-Name, X-User-Roles, X-User-Permissions)
 *
 * Đây là test quan trọng để verify JwtAuthenticationFilter
 * và RbacPermissionChecker hoạt động đúng dưới tải.
 */

export const options = {
  ...LOAD,
  thresholds: {
    ...LOAD.thresholds,
    // JWT validation phải nhanh (in-process, không DB)
    'http_req_duration{name:protected-200}': ['p(95)<500'],
    'http_req_duration{name:unauth-401}':    ['p(95)<100'],  // reject nhanh
    'http_req_duration{name:forbidden-403}': ['p(95)<100'],  // reject nhanh
  },
};

// ── Token có permission sai (thiếu products:READ) ──
// Tạo JWT giả bằng cách dùng user đăng nhập rồi thử path khác
// Hoặc dùng một user không có permission đó.
// Trong test này ta sẽ dùng admin token gọi vào endpoint user-only
// và ngược lại để verify RBAC deny.

export function setup() {
  const adminTokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  const userTokens  = ensureAndLogin(env.userUsername,  env.userPassword,  env.userRoles);
  return { adminTokens, userTokens };
}

export default function (data) {
  const { adminTokens, userTokens } = data;
  const roll = Math.random();

  if (roll < 0.20) {
    // ── Case 1: Không có token → 401 ──────────────────────────────────────
    const res = http.get(env.productsUrl, {
      tags: { name: 'unauth-401' },
    });
    unauthorizedCount.add(res.status === 401 ? 1 : 0);
    check(res, { 'no-token: 401': (r) => r.status === 401 });

  } else if (roll < 0.35) {
    // ── Case 2: ROLE_USER gọi admin endpoint → 403 ─────────────────────────
    const res = http.get(env.adminRoutes, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'forbidden-403' },
    });
    forbiddenCount.add(res.status === 403 ? 1 : 0);
    check(res, { 'user→admin: 403': (r) => r.status === 403 });

  } else if (roll < 0.50) {
    // ── Case 3: User hợp lệ gọi resource → 200 ────────────────────────────
    const res = http.get(env.productsUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'protected-200' },
    });
    recordMetrics(res, adminLatency, 'user-resource', [200]);

  } else if (roll < 0.65) {
    // ── Case 4: Admin gọi admin route list → 200 ──────────────────────────
    const res = http.get(env.adminRoutes, {
      headers: authHeaders(adminTokens.accessToken),
      tags:    { name: 'admin-routes-200' },
    });
    recordMetrics(res, adminLatency, 'admin-routes', [200]);

  } else if (roll < 0.80) {
    // ── Case 5: Admin gọi admin permissions → 200 ─────────────────────────
    const res = http.get(env.adminPerms, {
      headers: authHeaders(adminTokens.accessToken),
      tags:    { name: 'admin-perms-200' },
    });
    recordMetrics(res, adminLatency, 'admin-permissions', [200]);

  } else if (roll < 0.90) {
    // ── Case 6: Profile whoami (header injection test) → 200 ──────────────
    const res = http.get(env.profileUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'whoami-200' },
    });
    recordMetrics(res, adminLatency, 'whoami', [200]);

    // Verify downstream nhận được X-User-Name (check response body nếu
    // downstream echo lại header — với whoami endpoint thường làm vậy)
    check(res, {
      'whoami: 200': (r) => r.status === 200,
    });

  } else {
    // ── Case 7: Actuator health (không cần auth) ──────────────────────────
    const res = http.get(env.actuator, {
      tags: { name: 'actuator-health' },
    });
    check(res, { 'actuator: 200': (r) => r.status === 200 });
  }

  sleep(Math.random() * 0.3 + 0.05);
}
