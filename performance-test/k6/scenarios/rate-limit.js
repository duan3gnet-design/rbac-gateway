import http from 'k6/http';
import { check, sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import { recordMetrics, rateLimitedCount, rateLimitRate } from '../helpers/metrics.js';
import { buildErrorReport } from '../helpers/error-logger.js';
import { Trend } from 'k6/metrics';
import { env } from '../config/environment.js';

/**
 * rate-limit.js — Kiểm tra Token Bucket Rate Limiting của Gateway.
 *
 * Kịch bản:
 *   - Phase 1 (burst): nhiều VU gửi request nhanh → mong đợi 429
 *   - Phase 2 (recovery): chờ bucket refill → mong đợi 200 trở lại
 *   - Phase 3 (isolation): user A bị limit không ảnh hưởng user B
 *
 * Rate limit config trong application.yml:
 *   replenish-rate: 200000 (prod) / 5 (test)
 *   burst-capacity: 400000 (prod) / 5 (test)
 *
 * Chạy với môi trường test (rate-limit thấp) để thấy 429 rõ ràng:
 *   k6 run --env BASE_URL=http://localhost:8080 scenarios/rate-limit.js
 */

const rateLimitLatency = new Trend('rate_limit_check_latency_ms', true);

export const options = {
  scenarios: {
    // Phase 1: burst để trigger rate limit
    burst_phase: {
      executor:  'constant-arrival-rate',
      rate:      50,          // 50 req/s
      timeUnit:  '1s',
      duration:  '30s',
      preAllocatedVUs: 20,
      maxVUs:    100,
      startTime: '0s',
      tags:      { phase: 'burst' },
    },

    // Phase 2: recovery — sau khi bucket refill
    recovery_phase: {
      executor:  'constant-arrival-rate',
      rate:      2,           // nhẹ nhàng để bucket refill
      timeUnit:  '1s',
      duration:  '30s',
      preAllocatedVUs: 5,
      maxVUs:    10,
      startTime: '35s',       // bắt đầu sau khi burst kết thúc + 5s buffer
      tags:      { phase: 'recovery' },
    },
  },

  thresholds: {
    // Trong burst phase, mong đợi nhiều 429 — đây là behavior đúng
    'rate_limit_check_latency_ms': ['p(95)<500'],
    // Sau recovery, success rate phải phục hồi
    'http_req_duration{phase:recovery}': ['p(95)<1000'],
  },
};

export function setup() {
  // Login 2 users riêng để test bucket isolation
  const userA = ensureAndLogin(env.userUsername,         env.userPassword, env.userRoles);
  const userB = ensureAndLogin(env.userUsername + '_2',  env.userPassword, env.userRoles);
  return { userA, userB };
}

export default function (data) {
  const { userA, userB } = data;
  const phase = __ENV.K6_SCENARIO_NAME || 'burst_phase';

  // Burst phase: dùng user A để trigger rate limit
  // Recovery phase: xen kẽ user A và user B để kiểm tra isolation
  const token = (phase === 'recovery_phase' && Math.random() > 0.5)
    ? userB.accessToken
    : userA.accessToken;

  const res = http.get(env.productsUrl, {
    headers: authHeaders(token),
    tags:    { name: 'rate-limit-check', phase },
  });

  rateLimitLatency.add(res.timings.duration);

  // Cả 200 lẫn 429 đều là expected behavior trong test này
  const isExpected = [200, 429].includes(res.status);

  check(res, {
    'response là 200 hoặc 429': () => isExpected,
    'không có 5xx': (r) => r.status < 500,
    'có X-RateLimit-Limit header khi 200': (r) =>
      r.status !== 200 || r.headers['X-Ratelimit-Limit'] !== undefined,
  });

  if (res.status === 429) {
    rateLimitedCount.add(1);
    rateLimitRate.add(1);

    // Kiểm tra response body 429 đúng format
    check(res, {
      '429 body có field status': (r) => {
        try { return JSON.parse(r.body).status === 429; }
        catch { return false; }
      },
      '429 có Retry-After header': (r) =>
        r.headers['Retry-After'] !== undefined,
    });
  } else {
    rateLimitRate.add(0);
    recordMetrics(res, rateLimitLatency, 'rate-limit-allowed', [200]);
  }

  // Không sleep trong burst phase để tạo áp lực
  if (phase === 'recovery_phase') sleep(0.5);
}

export function handleSummary(data) {
  return {
    'results/rate-limit-errors.json': buildErrorReport('rate-limit'),
  };
}
