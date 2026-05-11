import http from 'k6/http';
import { check, sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import { recordMetrics, adminLatency } from '../helpers/metrics.js';
import { env } from '../config/environment.js';

/**
 * admin-route-management.js — Kiểm tra Admin Route CRUD API dưới tải.
 *
 * Kịch bản:
 *   - GET all routes / GET by id (read-heavy: 70%)
 *   - POST create route mới (15%)
 *   - PUT update route (10%)
 *   - DELETE route vừa tạo (5%)
 *
 * Mục đích:
 *   - Đảm bảo admin API responsive dưới concurrent load
 *   - Verify RouteRefreshPublisher → Redis pub/sub không gây bottleneck
 *   - Kiểm tra DatabaseRouteLocator reload không làm chậm request trong flight
 */

export const options = {
  scenarios: {
    admin_read: {
      executor: 'constant-arrival-rate',
      rate:     20,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 10,
      maxVUs: 30,
      tags: { scenario: 'admin-read' },
    },
    admin_write: {
      executor: 'constant-arrival-rate',
      rate:     2,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 3,
      maxVUs: 10,
      startTime: '15s',  // delay để read đã warm-up
      tags: { scenario: 'admin-write' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:admin-read}':  ['p(95)<500'],
    'http_req_duration{scenario:admin-write}': ['p(95)<1000'],
    'http_req_failed': ['rate<0.02'],
  },
};

// Prefix riêng để tránh conflict với seeded routes
const ROUTE_ID_PREFIX = 'k6-perf-test-';
let routeCounter = 0;

function uniqueRouteId() {
  return `${ROUTE_ID_PREFIX}${Date.now()}-${++routeCounter}`;
}

export function setup() {
  const tokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  return { tokens };
}

export default function (data) {
  const { tokens } = data;
  const headers    = authHeaders(tokens.accessToken);
  const roll       = Math.random();

  if (roll < 0.50) {
    // GET all routes
    const res = http.get(env.adminRoutes, {
      headers,
      tags: { name: 'admin-GET-all-routes' },
    });
    recordMetrics(res, adminLatency, 'admin-list-routes', [200]);
    check(res, { 'GET routes: array': (r) => {
      try { return Array.isArray(JSON.parse(r.body)); }
      catch { return false; }
    }});

  } else if (roll < 0.70) {
    // GET route by id (auth-login luôn tồn tại)
    const res = http.get(`${env.adminRoutes}/auth-login`, {
      headers,
      tags: { name: 'admin-GET-route-by-id' },
    });
    recordMetrics(res, adminLatency, 'admin-get-route', [200]);

  } else if (roll < 0.75) {
    // GET permissions list
    const res = http.get(env.adminPerms, {
      headers,
      tags: { name: 'admin-GET-permissions' },
    });
    recordMetrics(res, adminLatency, 'admin-list-permissions', [200]);

  } else if (roll < 0.88) {
    // POST create route mới
    const routeId = uniqueRouteId();
    const res = http.post(
      env.adminRoutes,
      JSON.stringify({
        id:         routeId,
        uri:        'http://k6-test-upstream:9999',
        predicates: JSON.stringify([{ name: 'Path', args: { pattern: `/api/k6-test/${routeId}/**` } }]),
        filters:    '[]',
        routeOrder: 999,
        enabled:    true,
      }),
      { headers, tags: { name: 'admin-POST-create-route' } },
    );
    recordMetrics(res, adminLatency, 'admin-create-route', [201]);

    // Nếu tạo thành công, thử toggle disabled
    if (res.status === 201) {
      sleep(0.1);
      const toggleRes = http.patch(
        `${env.adminRoutes}/${routeId}/toggle`,
        JSON.stringify({ enabled: false }),
        { headers, tags: { name: 'admin-PATCH-toggle' } },
      );
      recordMetrics(toggleRes, adminLatency, 'admin-toggle-route', [200]);
    }

  } else if (roll < 0.95) {
    // PUT update route (dùng route test tạm thời)
    const routeId = uniqueRouteId();

    // Tạo trước
    http.post(
      env.adminRoutes,
      JSON.stringify({
        id: routeId, uri: 'http://old-host:8000',
        predicates: '[]', filters: '[]', routeOrder: 998, enabled: true,
      }),
      { headers },
    );

    sleep(0.05);

    // Update
    const res = http.put(
      `${env.adminRoutes}/${routeId}`,
      JSON.stringify({
        id: routeId, uri: 'http://new-host:9000',
        predicates: '[]', filters: '[]', routeOrder: 997, enabled: false,
      }),
      { headers, tags: { name: 'admin-PUT-update-route' } },
    );
    recordMetrics(res, adminLatency, 'admin-update-route', [200]);

    // Cleanup
    sleep(0.05);
    http.del(`${env.adminRoutes}/${routeId}`, null, { headers });

  } else {
    // DELETE route vừa tạo — cleanup k6-test routes
    const routeId = uniqueRouteId();
    http.post(
      env.adminRoutes,
      JSON.stringify({
        id: routeId, uri: 'http://to-delete:1234',
        predicates: '[]', filters: '[]', routeOrder: 996, enabled: true,
      }),
      { headers },
    );

    sleep(0.05);

    const res = http.del(
      `${env.adminRoutes}/${routeId}`,
      null,
      { headers, tags: { name: 'admin-DELETE-route' } },
    );
    recordMetrics(res, adminLatency, 'admin-delete-route', [204]);
  }

  sleep(Math.random() * 0.2 + 0.05);
}

export function teardown(data) {
  // Cleanup tất cả k6-test routes còn sót lại
  const { tokens } = data;
  const headers    = authHeaders(tokens.accessToken);

  const listRes = http.get(env.adminRoutes, { headers });
  if (listRes.status !== 200) return;

  try {
    const routes = JSON.parse(listRes.body);
    routes
      .filter(r => r.id && r.id.startsWith(ROUTE_ID_PREFIX))
      .forEach(r => {
        http.del(`${env.adminRoutes}/${r.id}`, null, { headers });
      });
    console.log(`Teardown: đã cleanup k6 test routes.`);
  } catch (e) {
    console.error('Teardown cleanup lỗi:', e.message);
  }
}
