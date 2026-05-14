import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { logError } from './error-logger.js';

/**
 * metrics.js — custom metrics dùng chung cho tất cả scenarios.
 *
 * k6 custom metrics được khai báo ở module scope (không trong hàm)
 * để tránh duplicate metric name errors.
 */

// ── Latency trends (phân tách theo nhóm) ────────────────────────────────────

/** Latency của JWT auth flow (login, refresh) */
export const authLatency = new Trend('auth_latency_ms', true);

/** Latency của protected resource requests */
export const resourceLatency = new Trend('resource_latency_ms', true);

/** Latency của admin API calls */
export const adminLatency = new Trend('admin_latency_ms', true);

/** Latency end-to-end (gateway routing + downstream) */
export const gatewayLatency = new Trend('gateway_e2e_latency_ms', true);

// ── Error counters ───────────────────────────────────────────────────────────

/** Số request bị 401 Unauthorized */
export const unauthorizedCount = new Counter('errors_401_total');

/** Số request bị 403 Forbidden */
export const forbiddenCount = new Counter('errors_403_total');

/** Số request bị 429 Too Many Requests */
export const rateLimitedCount = new Counter('errors_429_total');

/** Số request bị 5xx */
export const serverErrorCount = new Counter('errors_5xx_total');

/** Số request bị Circuit Breaker open (503) */
export const circuitBreakerCount = new Counter('circuit_breaker_open_total');

// ── Business rates ───────────────────────────────────────────────────────────

/** Tỷ lệ request thành công (2xx) */
export const successRate = new Rate('success_rate');

/** Tỷ lệ request bị rate limit */
export const rateLimitRate = new Rate('rate_limit_rate');

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Record metrics từ một response và thực hiện check.
 *
 * @param {import('k6/http').Response} res
 * @param {Trend} latencyMetric - metric cụ thể cho loại request này
 * @param {string} checkName - tên hiển thị trong k6 output
 * @param {number[]} expectedStatuses - status codes được coi là thành công
 */
export function recordMetrics(res, latencyMetric, checkName, expectedStatuses = [200]) {
  const isSuccess = expectedStatuses.includes(res.status);

  latencyMetric.add(res.timings.duration);
  gatewayLatency.add(res.timings.duration);
  successRate.add(isSuccess);

  if (res.status === 401) unauthorizedCount.add(1);
  if (res.status === 403) forbiddenCount.add(1);
  if (res.status === 429) { rateLimitedCount.add(1); rateLimitRate.add(1); }
  else rateLimitRate.add(0);
  if (res.status >= 500) serverErrorCount.add(1);
  if (res.status === 503) circuitBreakerCount.add(1);

  check(res, {
    [`${checkName}: status in ${expectedStatuses}`]: () => isSuccess,
    [`${checkName}: response time < 2s`]: (r) => r.timings.duration < 2000,
  });

  // Log chi tiết request lỗi (status ngoài expectedStatuses)
  if (!isSuccess) logError(res, checkName, expectedStatuses);

  return isSuccess;
}
