import http from 'k6/http';
import { check, sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import {
  recordMetrics,
  resourceLatency,
  authLatency,
  adminLatency,
  rateLimitedCount,
  circuitBreakerCount,
  successRate,
} from '../helpers/metrics.js';
import { getErrorBuffer, buildErrorReport } from '../helpers/error-logger.js';
import { env } from '../config/environment.js';
import { STRESS } from '../config/options.js';

/**
 * full-system.js — Stress test toàn hệ thống với mixed workload.
 *
 * Mô phỏng traffic thực tế của một API gateway:
 *   - 35% GET resources (user traffic)
 *   - 20% POST/GET orders (transaction traffic)
 *   - 15% profile/whoami (identity-heavy)
 *   - 15% auth flows (login, refresh)
 *   - 10% admin operations (route management)
 *   -  5% public endpoints (no-auth)
 *
 * Mục đích:
 *   - Tìm bottleneck thực sự của toàn hệ thống
 *   - Verify Virtual Thread throughput dưới concurrent load
 *   - Stress test Redis (rate limit + route refresh pub/sub cùng lúc)
 *   - Kiểm tra PostgreSQL connection pool (HikariCP) không cạn kiệt
 */

export const options = {
  ...STRESS,
  thresholds: {
    ...STRESS.thresholds,
    // Gateway phải maintain throughput tốt với Virtual Threads
    'http_req_duration{name:GET-products}':   ['p(95)<600'],
    'http_req_duration{name:GET-orders}':     ['p(95)<600'],
    'http_req_duration{name:POST-auth-login}':['p(95)<800'],
    'http_req_duration{name:GET-admin}':      ['p(95)<500'],
    // Không có unexpected 5xx (503 từ CB là acceptable)
    'errors_5xx_total': ['count<50'],
  },
};

export function setup() {
  const adminTokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  const userTokens  = ensureAndLogin(env.userUsername,  env.userPassword,  env.userRoles);
  return { adminTokens, userTokens };
}

export default function (data) {
  const { adminTokens, userTokens } = data;
  const roll = Math.random();

  if (roll < 0.35) {
    // ── GET resources/products ──────────────────────────────────────────────
    const res = http.get(env.productsUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'GET-products' },
    });
    recordMetrics(res, resourceLatency, 'products', [200]);

    if (res.status === 429) rateLimitedCount.add(1);
    if (res.status === 503) circuitBreakerCount.add(1);

  } else if (roll < 0.55) {
    // ── GET/POST resources/orders ───────────────────────────────────────────
    const method = Math.random() > 0.7 ? 'POST' : 'GET';

    let res;
    if (method === 'GET') {
      res = http.get(env.ordersUrl, {
        headers: authHeaders(userTokens.accessToken),
        tags:    { name: 'GET-orders' },
      });
    } else {
      res = http.post(
        env.ordersUrl,
        JSON.stringify({ productId: 1, quantity: 1 }),
        {
          headers: authHeaders(userTokens.accessToken),
          tags:    { name: 'POST-orders' },
        },
      );
    }
    recordMetrics(res, resourceLatency, 'orders', [200, 201]);

  } else if (roll < 0.70) {
    // ── GET profile/whoami ──────────────────────────────────────────────────
    const res = http.get(env.profileUrl, {
      headers: authHeaders(userTokens.accessToken),
      tags:    { name: 'GET-whoami' },
    });
    recordMetrics(res, resourceLatency, 'whoami', [200]);

  } else if (roll < 0.80) {
    // ── Auth flows ─────────────────────────────────────────────────────────
    if (Math.random() > 0.6) {
      // Login — LoginRequest dùng field `username`
      const res = http.post(
        env.loginUrl,
        JSON.stringify({ username: env.userUsername, password: env.userPassword }),
        {
          headers: { 'Content-Type': 'application/json' },
          tags:    { name: 'POST-auth-login' },
        },
      );
      recordMetrics(res, authLatency, 'login', [200]);
    } else {
      // Refresh
      const res = http.post(
        env.refreshUrl,
        JSON.stringify({ refreshToken: userTokens.refreshToken }),
        {
          headers: { 'Content-Type': 'application/json' },
          tags:    { name: 'POST-auth-refresh' },
        },
      );
      recordMetrics(res, authLatency, 'refresh', [200]);
    }

  } else if (roll < 0.90) {
    // ── Admin reads ────────────────────────────────────────────────────────
    const adminEndpoint = Math.random() > 0.5 ? env.adminRoutes : env.adminPerms;
    const res = http.get(adminEndpoint, {
      headers: authHeaders(adminTokens.accessToken),
      tags:    { name: 'GET-admin' },
    });
    recordMetrics(res, adminLatency, 'admin', [200]);

  } else {
    // ── Public endpoint (no auth) ──────────────────────────────────────────
    const res = http.get(env.actuator, {
      tags: { name: 'GET-actuator-health' },
    });
    check(res, { 'actuator UP': (r) => r.status === 200 });
  }

  // Think time thực tế: 50ms - 300ms
  sleep(Math.random() * 0.25 + 0.05);
}

export function handleSummary(data) {
  const summary = {
    timestamp:   new Date().toISOString(),
    scenario:    'full-system-stress',
    duration:    data.state.testRunDurationMs,
    vus_max:     data.metrics.vus_max?.values?.max || 0,
    requests:    data.metrics.http_reqs?.values?.count || 0,
    req_per_sec: data.metrics.http_reqs?.values?.rate?.toFixed(2) || 0,
    p95_ms:      data.metrics.http_req_duration?.values?.['p(95)']?.toFixed(2) || 0,
    p99_ms:      data.metrics.http_req_duration?.values?.['p(99)']?.toFixed(2) || 0,
    error_rate:  ((data.metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2) + '%',
    success_rate:((data.metrics.success_rate?.values?.rate || 0) * 100).toFixed(2) + '%',
    rate_limited: data.metrics.errors_429_total?.values?.count || 0,
    cb_trips:    data.metrics.circuit_breaker_open_total?.values?.count || 0,
  };

  console.log('\n═══════════════════════════════════════════');
  console.log('  FULL SYSTEM STRESS TEST — SUMMARY');
  console.log('═══════════════════════════════════════════');
  Object.entries(summary).forEach(([k, v]) => {
    console.log(`  ${k.padEnd(20)}: ${v}`);
  });
  console.log('═══════════════════════════════════════════\n');

  const errorBuffer = getErrorBuffer();
  if (errorBuffer.length > 0) {
    console.warn(`[ERROR LOG] ${errorBuffer.length} lỗi được ghi vào results/full-system-errors.json`);
  }

  return {
    'results/full-system-summary.json': JSON.stringify(summary, null, 2),
    'results/full-system-errors.json':  buildErrorReport('full-system'),
    stdout: '\nFull system stress test completed.\n',
  };
}
