import http    from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray }         from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import { ensureAndLogin, refreshAccessToken, authHeaders } from '../helpers/auth.js';
import {
  recordMetrics,
  resourceLatency,
  authLatency,
  adminLatency,
  rateLimitedCount,
  circuitBreakerCount,

} from '../helpers/metrics.js';
import { getErrorBuffer, buildErrorReport } from '../helpers/error-logger.js';
import { env }    from '../config/environment.js';
import { STRESS } from '../config/options.js';

// ═══════════════════════════════════════════════════════════════════════════════
//  full-system.js — Full-system load test với nhiều profiles
//
//  Chạy theo profile (qua env TEST_PROFILE):
//    SMOKE      — 1 VU, 1 phút (sanity check)
//    LOAD       — ramp lên 50 VUs, sustain 5 phút
//    STRESS     — đẩy tới 300 VUs, tìm điểm gãy
//    SOAK       — 20 VUs × 30 phút, phát hiện memory leak
//    SPIKE      — burst đột ngột 500 VUs trong 30 giây
//    BREAKPOINT — tăng dần từng bậc, xác định throughput tối đa
//
//  Traffic mix (mô phỏng production):
//    30% — GET products          (user traffic chủ đạo)
//    15% — GET orders            (read transaction)
//    10% — POST orders           (write transaction)
//    10% — GET profile/whoami    (identity check, downstream header injection)
//     8% — POST auth/login       (session create)
//     7% — POST auth/refresh     (token rotate)
//     8% — Admin reads           (ops traffic)
//     7% — Admin writes          (route CRUD, permission assign)
//     5% — Public / actuator     (health probe)
//
//  Mục tiêu kiểm tra:
//    ✓ Virtual Thread throughput (Spring Boot 4 + Java 21)
//    ✓ HikariCP pool (gateway + auth + resource, tổng 3 datasources)
//    ✓ Redis rate limiter không làm bottleneck ở high VUs
//    ✓ Redis pub/sub route refresh không block request pipeline
//    ✓ Resilience4j CB & Retry không thêm latency ngoài ý muốn
//    ✓ RBAC cache (RbacPermissionChecker) hit rate > 95% dưới tải
//    ✓ JWT validation (in-memory, không DB) < 10ms p95
//    ✓ Token tự refresh sau khi hết hạn
// ═══════════════════════════════════════════════════════════════════════════════

// ── Custom metrics riêng cho full-system ─────────────────────────────────────

const tokenRefreshCount  = new Counter('token_refresh_total');
const adminWriteLatency  = new Trend('admin_write_latency_ms', true);
const orderWriteLatency  = new Trend('order_write_latency_ms', true);
const rbacDenyCount      = new Counter('rbac_deny_total');
const profileLatency     = new Trend('profile_latency_ms', true);
const publicLatency      = new Trend('public_latency_ms', true);
const tokenExpiredCount  = new Counter('token_expired_401_total');
const p99BreachCount     = new Counter('p99_breach_total');   // > 2000ms

// ── Profile → options mapping ─────────────────────────────────────────────────

const PROFILES = {
  SMOKE: {
    vus: 1,
    duration: '1m',
    thresholds: buildThresholds({ errorRate: 0.01, p95: 800 }),
  },

  LOAD: {
    stages: [
      { duration: '1m',   target: 20  },   // warm-up
      { duration: '30s',  target: 50  },   // ramp
      { duration: '5m',   target: 50  },   // sustain — đủ lâu để CB/RL ổn định
      { duration: '30s',  target: 0   },   // cool-down
    ],
    thresholds: buildThresholds({ errorRate: 0.01, p95: 800, p99: 1500 }),
  },

  STRESS: {
    stages: [
      { duration: '1m',   target: 50  },
      { duration: '1m',   target: 150 },
      { duration: '2m',   target: 300 },   // push to max
      { duration: '1m',   target: 150 },   // partial recover
      { duration: '30s',  target: 0   },
    ],
    thresholds: buildThresholds({ errorRate: 0.05, p95: 2000, p99: 4000 }),
  },

  SOAK: {
    stages: [
      { duration: '2m',  target: 20 },
      { duration: '30m', target: 20 },     // 30 phút để phát hiện memory leak
      { duration: '2m',  target: 0  },
    ],
    thresholds: buildThresholds({ errorRate: 0.01, p95: 1000 }),
  },

  SPIKE: {
    stages: [
      { duration: '30s', target: 10  },    // baseline thấp
      { duration: '10s', target: 500 },    // spike đột ngột
      { duration: '30s', target: 500 },    // sustain spike
      { duration: '10s', target: 10  },    // drop đột ngột
      { duration: '1m',  target: 10  },    // verify recovery
    ],
    thresholds: buildThresholds({ errorRate: 0.20, p95: 4000 }),
  },

  BREAKPOINT: {
    stages: [
      { duration: '2m', target: 50  },
      { duration: '2m', target: 100 },
      { duration: '2m', target: 200 },
      { duration: '2m', target: 300 },
      { duration: '2m', target: 400 },
      { duration: '2m', target: 500 },
      { duration: '1m', target: 0   },
    ],
    thresholds: buildThresholds({ errorRate: 0.15, p95: 5000 }),
  },
};

function buildThresholds({ errorRate = 0.05, p95 = 2000, p99 = 5000 } = {}) {
  return {
    http_req_failed:                               [`rate<${errorRate}`],
    http_req_duration:                             [`p(95)<${p95}`, `p(99)<${p99}`],
    success_rate:                                  ['rate>0.90'],

    // Per-group latency SLOs
    'http_req_duration{name:GET-products}':        [`p(95)<${p95}`],
    'http_req_duration{name:GET-orders}':          [`p(95)<${p95}`],
    'http_req_duration{name:POST-orders}':         [`p(95)<${Math.round(p95 * 1.5)}`],
    'http_req_duration{name:GET-profile}':         [`p(95)<${p95}`],
    'http_req_duration{name:POST-login}':          [`p(95)<${Math.round(p95 * 1.2)}`],
    'http_req_duration{name:POST-refresh}':        [`p(95)<${p95}`],
    'http_req_duration{name:GET-admin-routes}':    [`p(95)<${p95}`],
    'http_req_duration{name:GET-admin-perms}':     [`p(95)<${p95}`],

    // Circuit breaker không nên trip quá nhiều (< 1% requests)
    'circuit_breaker_open_total': [`count<${Math.ceil(p95 * 0.5)}`],
  };
}

// ── Chọn profile ─────────────────────────────────────────────────────────────

const profileName = (__ENV.TEST_PROFILE || 'STRESS').toUpperCase();
const selectedProfile = PROFILES[profileName] || PROFILES.STRESS;

export const options = {
  ...selectedProfile,
  // Grace period để VU teardown sau khi test kết thúc
  gracefulStop:    '30s',
  gracefulRampDown: '20s',
};

// ── Static data (SharedArray để không duplicate giữa các VUs) ────────────────
new SharedArray('productIds', () => [1, 2]);
const ORDER_ITEMS = new SharedArray('orderItems', () => [
  [{ productId: 1, quantity: 1 }],
  [{ productId: 2, quantity: 2 }],
  [{ productId: 1, quantity: 1 }],
]);

// ════════════════════════════════════════════════════════════════════════════
//  SETUP — chạy 1 lần trước khi VUs bắt đầu
// ════════════════════════════════════════════════════════════════════════════

export function setup() {
  console.log(`\n[setup] Profile: ${profileName}`);
  console.log(`[setup] Base URL: ${env.baseUrl}`);

  const adminTokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  const userTokens  = ensureAndLogin(env.userUsername,  env.userPassword,  env.userRoles);

  console.log(`[setup] Login OK — admin=${env.adminUsername}, user=${env.userUsername}\n`);
  return { adminTokens, userTokens };
}

// ════════════════════════════════════════════════════════════════════════════
//  MAIN VU FUNCTION
// ════════════════════════════════════════════════════════════════════════════

export default function (data) {
  // Mỗi VU tự quản lý token state (không shared giữa VUs)
  let { adminTokens, userTokens } = data;

  // ── Token auto-refresh: nếu nhận 401 thì thử refresh 1 lần ──────────────
  //    (được xử lý bên trong từng scenario branch bên dưới)

  const roll = Math.random();

  // ── 30% — GET products ────────────────────────────────────────────────────
  if (roll < 0.30) {
    group('resource: GET products', () => {
      const res = http.get(env.productsUrl, {
        headers: authHeaders(userTokens.accessToken),
        tags:    { name: 'GET-products' },
      });

      // Auto-refresh nếu 401
      if (res.status === 401) {
        tokenExpiredCount.add(1);
        const newToken = refreshAccessToken(userTokens.refreshToken);
        if (newToken) { userTokens.accessToken = newToken; tokenRefreshCount.add(1); }
        return;
      }

      recordMetrics(res, resourceLatency, 'products', [200]);
      if (res.status === 429) rateLimitedCount.add(1);
      if (res.status === 503) circuitBreakerCount.add(1);
      if (res.timings.duration > 2000) p99BreachCount.add(1);
    });

  // ── 15% — GET orders ──────────────────────────────────────────────────────
  } else if (roll < 0.45) {
    group('resource: GET orders', () => {
      const res = http.get(env.ordersUrl, {
        headers: authHeaders(userTokens.accessToken),
        tags:    { name: 'GET-orders' },
      });

      if (res.status === 401) {
        tokenExpiredCount.add(1);
        const newToken = refreshAccessToken(userTokens.refreshToken);
        if (newToken) { userTokens.accessToken = newToken; tokenRefreshCount.add(1); }
        return;
      }

      recordMetrics(res, resourceLatency, 'orders', [200]);
      if (res.status === 429) rateLimitedCount.add(1);
      if (res.timings.duration > 2000) p99BreachCount.add(1);
    });

  // ── 10% — POST orders (write transaction) ─────────────────────────────────
  } else if (roll < 0.55) {
    group('resource: POST orders', () => {
      const items = ORDER_ITEMS[Math.floor(Math.random() * ORDER_ITEMS.length)];
      const res = http.post(
        env.ordersUrl,
        JSON.stringify({ items }),
        {
          headers: authHeaders(userTokens.accessToken),
          tags:    { name: 'POST-orders' },
        },
      );

      if (res.status === 401) {
        tokenExpiredCount.add(1);
        const newToken = refreshAccessToken(userTokens.refreshToken);
        if (newToken) { userTokens.accessToken = newToken; tokenRefreshCount.add(1); }
        return;
      }

      orderWriteLatency.add(res.timings.duration);
      recordMetrics(res, resourceLatency, 'orders-write', [200, 201]);
      if (res.status === 429) rateLimitedCount.add(1);
      if (res.timings.duration > 2000) p99BreachCount.add(1);
    });

  // ── 10% — GET profile/whoami (header injection test) ─────────────────────
  } else if (roll < 0.65) {
    group('resource: GET profile', () => {
      const res = http.get(env.profileUrl, {
        headers: authHeaders(userTokens.accessToken),
        tags:    { name: 'GET-profile' },
      });

      profileLatency.add(res.timings.duration);
      recordMetrics(res, resourceLatency, 'profile', [200]);

      // Verify header injection: resource-service trả X-User-Name trong response body?
      check(res, {
        'profile: 200': (r) => r.status === 200,
        'profile: body non-empty': (r) => r.body && r.body.length > 0,
      });
    });

  // ── 8% — POST auth/login ──────────────────────────────────────────────────
  } else if (roll < 0.73) {
    group('auth: POST login', () => {
      const res = http.post(
        env.loginUrl,
        JSON.stringify({ username: env.userUsername, password: env.userPassword }),
        {
          headers: { 'Content-Type': 'application/json' },
          tags:    { name: 'POST-login' },
        },
      );

      recordMetrics(res, authLatency, 'login', [200]);

      // Verify token shape
      check(res, {
        'login: has accessToken': (r) => {
          try { return !!JSON.parse(r.body).token; }
          catch { return false; }
        },
        'login: has refreshToken': (r) => {
          try { return !!JSON.parse(r.body).refreshToken; }
          catch { return false; }
        },
      });
    });

  // ── 7% — POST auth/refresh ────────────────────────────────────────────────
  // } else if (roll < 0.80) {
  //   group('auth: POST refresh', () => {
  //     if (!userTokens.refreshToken) return;
  //
  //     const res = http.post(
  //       env.refreshUrl,
  //       JSON.stringify({ refreshToken: userTokens.refreshToken }),
  //       {
  //         headers: { 'Content-Type': 'application/json' },
  //         tags:    { name: 'POST-refresh' },
  //       },
  //     );
  //
  //     recordMetrics(res, authLatency, 'refresh', [200]);
  //
  //     if (res.status === 200) {
  //       try {
  //         const body = JSON.parse(res.body);
  //         if (body.token) {
  //           userTokens.accessToken = body.token;
  //           tokenRefreshCount.add(1);
  //         }
  //       } catch { /* parse failed — không update token */ }
  //     }
  //   });

  // ── 8% — Admin reads ──────────────────────────────────────────────────────
  } else if (roll < 0.88) {
    group('admin: reads', () => {
      const endpoint = Math.random() < 0.6 ? env.adminRoutes : env.adminPerms;
      const tag      = endpoint === env.adminRoutes ? 'GET-admin-routes' : 'GET-admin-perms';

      const res = http.get(endpoint, {
        headers: authHeaders(adminTokens.accessToken),
        tags:    { name: tag },
      });

      recordMetrics(res, adminLatency, tag, [200]);

      // Verify RBAC: user token gọi admin → 403
      if (Math.random() < 0.10) {   // 10% của nhánh này → cross-check RBAC
        const rbacRes = http.get(env.adminRoutes, {
          headers: authHeaders(userTokens.accessToken),
          tags:    { name: 'RBAC-deny-check' },
        });
        if (rbacRes.status === 403) rbacDenyCount.add(1);
        check(rbacRes, { 'RBAC: user -> admin 403': (r) => r.status === 403 });
      }
    });

  // ── 7% — Admin writes (route CRUD + permission assign) ───────────────────
  } else if (roll < 0.95) {
    group('admin: writes', () => {
      const routeId = `perf-route-${__VU}-${__ITER}`;

      // CREATE route
      const createRes = http.post(
        env.adminRoutes,
        JSON.stringify({
          id:         routeId,
          uri:        `http://localhost:808${(__VU % 3) + 1}`,
          predicates: JSON.stringify([{ name: 'Path', args: { pattern: `/perf/${routeId}/**` } }]),
          filters:    '[]',
          routeOrder: 100 + __VU,
          enabled:    false,    // disabled → không ảnh hưởng gateway routing
        }),
        {
          headers: authHeaders(adminTokens.accessToken),
          tags:    { name: 'POST-admin-route' },
        },
      );

      adminWriteLatency.add(createRes.timings.duration);
      recordMetrics(createRes, adminLatency, 'admin-create', [201]);

      if (createRes.status !== 201) return;

      sleep(0.05);

      // UPDATE route (toggle enabled)
      const toggleRes = http.patch(
        `${env.adminRoutes}/${routeId}/toggle`,
        JSON.stringify({ enabled: true }),
        {
          headers: authHeaders(adminTokens.accessToken),
          tags:    { name: 'PATCH-admin-toggle' },
        },
      );
      adminWriteLatency.add(toggleRes.timings.duration);
      recordMetrics(toggleRes, adminLatency, 'admin-toggle', [200]);

      sleep(0.05);

      // DELETE route (cleanup)
      const deleteRes = http.del(
        `${env.adminRoutes}/${routeId}`,
        null,
        {
          headers: authHeaders(adminTokens.accessToken),
          tags:    { name: 'DELETE-admin-route' },
        },
      );
      adminWriteLatency.add(deleteRes.timings.duration);
      recordMetrics(deleteRes, adminLatency, 'admin-delete', [204]);
    });

  // ── 5% — Public / actuator health ────────────────────────────────────────
  } else {
    group('public: health probe', () => {
      const res = http.get(env.actuator, {
        tags: { name: 'GET-actuator' },
      });

      publicLatency.add(res.timings.duration);
      check(res, {
        'actuator: UP':     (r) => r.status === 200,
        'actuator: status': (r) => {
          try { return JSON.parse(r.body).status === 'UP'; }
          catch { return false; }
        },
      });
    });
  }

  // Think time: 50ms–300ms (phân phối lệch về phía thấp, mô phỏng real traffic)
  sleep(0.05 + Math.random() * 0.25);
}

// ════════════════════════════════════════════════════════════════════════════
//  TEARDOWN — in warning nếu có nhiều lỗi
// ════════════════════════════════════════════════════════════════════════════

export function teardown() {
  const errors = getErrorBuffer();
  if (errors.length > 100) {
    console.warn(`\n[teardown] ⚠  ${errors.length} lỗi được ghi lại trong test. Xem results/full-system-errors.json\n`);
  }
}

// ════════════════════════════════════════════════════════════════════════════
//  SUMMARY
// ════════════════════════════════════════════════════════════════════════════

export function handleSummary(data) {
  const m = data.metrics;

  const get = (metric, agg) => {
    const v = m[metric]?.values;
    if (!v) return 'N/A';
    const val = agg === 'rate'  ? v.rate
              : agg === 'count' ? v.count
              : agg === 'p95'   ? v['p(95)']
              : agg === 'p99'   ? v['p(99)']
              : agg === 'max'   ? v.max
              : agg === 'avg'   ? v.mean
              : null;
    if (val == null) return 'N/A';
    if (agg === 'rate') return (val * 100).toFixed(2) + '%';
    if (typeof val === 'number') return val.toFixed(2);
    return String(val);
  };

  const summary = {
    scenario:          'full-system',
    profile:           profileName,
    timestamp:         new Date().toISOString(),
    duration_ms:       data.state.testRunDurationMs,

    // Throughput
    total_requests:    get('http_reqs',     'count'),
    req_per_sec:       get('http_reqs',     'rate'),

    // Latency (overall)
    p95_ms:            get('http_req_duration', 'p95'),
    p99_ms:            get('http_req_duration', 'p99'),
    max_ms:            get('http_req_duration', 'max'),
    avg_ms:            get('http_req_duration', 'avg'),

    // Latency (per group)
    products_p95_ms:   get('http_req_duration{name:GET-products}',     'p95'),
    orders_r_p95_ms:   get('http_req_duration{name:GET-orders}',       'p95'),
    orders_w_p95_ms:   get('http_req_duration{name:POST-orders}',      'p95'),
    profile_p95_ms:    get('http_req_duration{name:GET-profile}',      'p95'),
    login_p95_ms:      get('http_req_duration{name:POST-login}',       'p95'),
    refresh_p95_ms:    get('http_req_duration{name:POST-refresh}',     'p95'),
    admin_r_p95_ms:    get('http_req_duration{name:GET-admin-routes}', 'p95'),
    admin_w_p95_ms:    get('admin_write_latency_ms',                   'p95'),

    // Quality
    error_rate:        get('http_req_failed',  'rate'),
    success_rate:      get('success_rate',     'rate'),

    // Infrastructure signals
    rate_limited:      get('errors_429_total',           'count'),
    cb_trips:          get('circuit_breaker_open_total', 'count'),
    token_refreshes:   get('token_refresh_total',        'count'),
    token_401s:        get('token_expired_401_total',    'count'),
    rbac_denies:       get('rbac_deny_total',            'count'),
    p99_breaches:      get('p99_breach_total',           'count'),

    // VU peak
    vus_max:           m.vus_max?.values?.max ?? 'N/A',
  };

  // ── Console summary ──────────────────────────────────────────────────────

  const sep = '═'.repeat(52);
  console.log(`\n${sep}`);
  console.log(`  FULL SYSTEM — ${profileName} TEST SUMMARY`);
  console.log(sep);

  const section = (title) => console.log(`\n  ── ${title}`);
  const row     = (k, v) => console.log(`  ${k.padEnd(24)}: ${v}`);

  section('Throughput');
  row('Total requests',     summary.total_requests);
  row('Req/sec',            summary.req_per_sec);
  row('VUs peak',           summary.vus_max);
  row('Duration',           Math.round(data.state.testRunDurationMs / 1000) + 's');

  section('Latency (overall)');
  row('p95',  summary.p95_ms  + ' ms');
  row('p99',  summary.p99_ms  + ' ms');
  row('max',  summary.max_ms  + ' ms');
  row('avg',  summary.avg_ms  + ' ms');

  section('Latency (per group)');
  row('GET products p95',   summary.products_p95_ms  + ' ms');
  row('GET orders p95',     summary.orders_r_p95_ms  + ' ms');
  row('POST orders p95',    summary.orders_w_p95_ms  + ' ms');
  row('GET profile p95',    summary.profile_p95_ms   + ' ms');
  row('POST login p95',     summary.login_p95_ms     + ' ms');
  row('POST refresh p95',   summary.refresh_p95_ms   + ' ms');
  row('GET admin-routes p95', summary.admin_r_p95_ms + ' ms');
  row('Admin writes p95',   summary.admin_w_p95_ms   + ' ms');

  section('Quality & Errors');
  row('Error rate',         summary.error_rate);
  row('Success rate',       summary.success_rate);
  row('Rate limited (429)', summary.rate_limited);
  row('CB trips (503)',     summary.cb_trips);
  row('p99 breaches',       summary.p99_breaches);

  section('Auth & RBAC');
  row('Token refreshes',    summary.token_refreshes);
  row('Token expired 401',  summary.token_401s);
  row('RBAC denies (403)',   summary.rbac_denies);

  console.log(`\n${sep}\n`);

  // ── Files ────────────────────────────────────────────────────────────────

  const ts = new Date().toISOString().replace(/[:.]/g, '').slice(0, 15);

  return {
    [`results/full-system-summary.json`]:          JSON.stringify(summary, null, 2),
    [`results/full-system_${ts}.json`]:            JSON.stringify(summary, null, 2),
    [`results/full-system-errors.json`]:           buildErrorReport('full-system'),
    stdout: `\nFull-system ${profileName} test completed. Xem results/ để chi tiết.\n`,
  };
}
