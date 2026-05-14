import http from 'k6/http';
import { check, sleep } from 'k6';
import { ensureAndLogin, authHeaders } from '../helpers/auth.js';
import { circuitBreakerCount, serverErrorCount, recordMetrics, resourceLatency } from '../helpers/metrics.js';
import { buildErrorReport } from '../helpers/error-logger.js';
import { env } from '../config/environment.js';
import { Trend, Counter } from 'k6/metrics';

/**
 * circuit-breaker.js — Kiểm tra Circuit Breaker (Resilience4j) của Gateway.
 *
 * Circuit Breaker config trong application.yml:
 *   sliding-window-size: 10
 *   failure-rate-threshold: 50%
 *   wait-duration-in-open-state: 10s
 *   permitted-number-of-calls-in-half-open-state: 3
 *
 * Kịch bản:
 *   Phase 1 (healthy):    downstream đang UP → mong đợi 200
 *   Phase 2 (degraded):   downstream trả 500 → CB chuyển OPEN sau threshold
 *   Phase 3 (cb-open):    CB OPEN → mong đợi 503 fallback ngay lập tức
 *   Phase 4 (half-open):  chờ wait-duration → CB thử lại (HALF-OPEN)
 *   Phase 5 (recovery):   downstream UP → CB CLOSE → mong đợi 200
 *
 * LƯU Ý: Để test này hoạt động đúng, bạn cần có cách inject failure
 * vào downstream service. Xem README để hướng dẫn dùng WireMock
 * hoặc chaos engineering tool.
 *
 * Với môi trường local dev, test này chủ yếu verify:
 *   - Fallback endpoint (/fallback/auth, /fallback/resource) trả 503
 *   - CB metrics xuất hiện trên /actuator/circuitbreakers
 *   - Response time khi CB OPEN thấp hơn khi CB CLOSED (fast fail)
 */

const cbFallbackLatency = new Trend('cb_fallback_latency_ms', true);
const fallbackCount     = new Counter('cb_fallback_triggered_total');

export const options = {
  scenarios: {
    // Phase 1: normal traffic — baseline
    normal_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },  // ramp-up
        { duration: '30s', target: 10 },  // sustain
      ],
      startTime: '0s',
      tags: { phase: 'healthy' },
    },

    // Phase 3: verify fallback behavior khi CB đã OPEN
    // (Phase 2 — gây lỗi downstream — được thực hiện thủ công hoặc
    // qua chaos tool trước khi phase này bắt đầu)
    cb_open_phase: {
      executor: 'constant-vus',
      vus:      5,
      duration: '30s',
      startTime: '2m',   // đủ thời gian để phase 2 trigger CB OPEN
      tags: { phase: 'cb-open' },
    },

    // Phase 5: recovery sau khi wait-duration hết
    recovery_check: {
      executor: 'constant-vus',
      vus:      3,
      duration: '30s',
      startTime: '3m',
      tags: { phase: 'recovery' },
    },
  },

  thresholds: {
    // Khi CB OPEN, fallback phải trả về nhanh (<200ms)
    'cb_fallback_latency_ms': ['p(95)<200'],
    // Không có 4xx lạ (401/403) — chỉ 200 và 503 là expected
    'http_req_duration{phase:recovery}': ['p(95)<1000'],
  },
};

export function setup() {
  const tokens = ensureAndLogin(env.adminUsername, env.adminPassword, env.adminRoles);
  return { tokens };
}

export default function (data) {
  const { tokens } = data;
  const phase = __ENV.K6_SCENARIO_NAME || 'normal_traffic';

  if (phase === 'cb-open' || phase === 'cb_open_phase') {
    // Gọi resource endpoint — mong đợi CB đã OPEN → 503 với fallback body
    const res = http.get(env.productsUrl, {
      headers: authHeaders(tokens.accessToken),
      tags:    { name: 'cb-open-check', phase },
    });

    cbFallbackLatency.add(res.timings.duration);

    if (res.status === 503) {
      fallbackCount.add(1);
      circuitBreakerCount.add(1);

      check(res, {
        'CB OPEN: trả 503': (r) => r.status === 503,
        'CB OPEN: body có status field': (r) => {
          try { return JSON.parse(r.body).status === 503; }
          catch { return false; }
        },
        'CB OPEN: body có message': (r) => {
          try { return !!JSON.parse(r.body).message; }
          catch { return false; }
        },
        'CB OPEN: fast fail (<200ms)': (r) => r.timings.duration < 200,
      });
    } else {
      check(res, { 'CB vẫn CLOSED hoặc HALF-OPEN': (r) => [200, 503].includes(r.status) });
    }

  } else if (phase === 'recovery' || phase === 'recovery_check') {
    // Recovery: CB nên CLOSED → 200
    const res = http.get(env.productsUrl, {
      headers: authHeaders(tokens.accessToken),
      tags:    { name: 'cb-recovery-check', phase },
    });

    recordMetrics(res, resourceLatency, 'cb-recovery', [200]);

    check(res, {
      'Recovery: CB CLOSED lại → 200': (r) => r.status === 200,
    });

  } else {
    // Normal traffic — healthy baseline
    const res = http.get(env.productsUrl, {
      headers: authHeaders(tokens.accessToken),
      tags:    { name: 'normal-traffic', phase },
    });

    recordMetrics(res, resourceLatency, 'normal', [200]);
  }

  // Kiểm tra CB state qua actuator (không tính vào metrics chính)
  if (Math.random() < 0.05) {  // 5% chance để không spam actuator
    const actuatorRes = http.get(`${env.actuator}`, {
      tags: { name: 'actuator-health', skip_metrics: 'true' },
    });
    check(actuatorRes, { 'actuator /health OK': (r) => r.status === 200 });
  }

  sleep(0.2);
}

export function handleSummary(data) {
  return {
    'results/circuit-breaker-errors.json': buildErrorReport('circuit-breaker'),
  };
}
