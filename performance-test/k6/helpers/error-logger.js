import { Counter } from 'k6/metrics';

/**
 * error-logger.js — Log chi tiết từng request lỗi ra console và file JSON.
 *
 * Tích hợp sẵn vào recordMetrics() trong metrics.js — tất cả scenarios
 * tự động có error logging mà không cần sửa từng file riêng.
 *
 * Output:
 *   - Console: log ngay khi lỗi xảy ra (thấy được trong k6 stdout)
 *   - File:    results/errors-<timestamp>.json (xem sau khi test xong)
 *
 * Format mỗi entry:
 * {
 *   "ts":        "2025-03-01T10:00:00.123Z",
 *   "vu":        12,
 *   "iter":      345,
 *   "scenario":  "full-system",
 *   "tag":       "GET-products",
 *   "url":       "http://api-gateway:8080/api/resources/products",
 *   "method":    "GET",
 *   "status":    503,
 *   "duration":  45.23,
 *   "body":      "{\"status\":503,\"message\":\"Circuit breaker open\"}",
 *   "errorType": "circuit_breaker"
 * }
 *
 * errorType phân loại tự động:
 *   "unauthorized"    → 401
 *   "forbidden"       → 403
 *   "rate_limited"    → 429
 *   "circuit_breaker" → 503
 *   "server_error"    → 5xx khác
 *   "unexpected"      → status ngoài expected list
 */

// ── Metrics ─────────────────────────────────────────────────────────────────

/** Tổng lỗi đã log (không tag — dùng cho tổng hợp nhanh) */
export const errorLoggedCount = new Counter('error_logged_total');

/**
 * Lỗi theo URL + error_type + method — dùng để drill down trên Grafana.
 * Tag được ghi vào InfluxDB, query được bằng GROUP BY tag.
 *
 * Ví dụ query Grafana:
 *   SELECT sum("value") FROM "errors_by_url"
 *   WHERE $timeFilter
 *   GROUP BY time($__interval), "url", "error_type"
 */
export const errorByUrl = new Counter('errors_by_url');

// ── Buffer lỗi — flush ra handleSummary() ──────────────────────────────────
// Dùng array thay vì ghi file trực tiếp vì k6 không hỗ trợ
// write file trong VU context (chỉ read được qua k6/experimental/fs).
// handleSummary() nhận buffer này và ghi ra file.
const _errorBuffer = [];

/** Độ dài tối đa của response body được log (tránh spam với HTML error page) */
const MAX_BODY_LENGTH = 500;

/** Số lỗi tối đa buffer trong memory (tránh OOM với STRESS test 500 VUs) */
const MAX_BUFFER_SIZE = 2000;

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * Log một request lỗi.
 * Gọi tự động từ recordMetrics() — không cần gọi thủ công trong scenarios.
 *
 * @param {import('k6/http').Response} res       - response từ k6 http
 * @param {string}                     checkName - tên check (dùng làm tag)
 * @param {number[]}                   expectedStatuses
 */
export function logError(res, checkName, expectedStatuses) {
  if (_errorBuffer.length >= MAX_BUFFER_SIZE) return;

  const status    = res.status;
  const errorType = classifyError(status, expectedStatuses);

  if (!errorType) return; // không phải lỗi

  const body = truncate(res.body, MAX_BODY_LENGTH);

  const entry = {
    ts:       new Date().toISOString(),
    vu:       __VU,
    iter:     __ITER,
    scenario: __ENV.K6_SCENARIO_NAME || 'unknown',
    tag:      checkName,
    url:      res.url,
    method:   res.request ? res.request.method : 'unknown',
    status,
    duration: Math.round(res.timings.duration * 100) / 100,
    body,
    errorType,
  };

  _errorBuffer.push(entry);
  errorLoggedCount.add(1);

  // Ghi metric có tag để Grafana có thể GROUP BY url / error_type / method
  const normalizedUrl = normalizeUrl(res.url);
  errorByUrl.add(1, {
    endpoint:   normalizedUrl,
    method:     entry.method,
    status:     String(status),
    error_type: errorType,
  });

  // Log ra console ngay lập tức để thấy trong stdout khi đang chạy
  // Chỉ log level WARN/ERROR để không spam với INFO
  if (status >= 500) {
    console.error(`[ERROR] ${entry.method} ${entry.url} → ${status} (${entry.duration}ms) | ${truncate(body, 120)}`);
  } else if (status === 429) {
    console.warn(`[RATE_LIMIT] ${entry.method} ${entry.url} → 429 (${entry.duration}ms)`);
  } else if (status === 401 || status === 403) {
    console.warn(`[AUTH] ${entry.method} ${entry.url} → ${status} (${entry.duration}ms)`);
  } else {
    console.warn(`[UNEXPECTED] ${entry.method} ${entry.url} → ${status} expected=[${expectedStatuses}] (${entry.duration}ms)`);
  }
}

/**
 * Trả về buffer lỗi để handleSummary() ghi ra file.
 *
 * Dùng trong handleSummary():
 * @example
 * import { getErrorBuffer, buildErrorSummary } from '../helpers/error-logger.js';
 *
 * export function handleSummary(data) {
 *   return {
 *     'results/errors.json': JSON.stringify(getErrorBuffer(), null, 2),
 *     ...buildErrorSummary(data),
 *   };
 * }
 *
 * @returns {object[]}
 */
export function getErrorBuffer() {
  return _errorBuffer;
}

/**
 * Tạo nội dung file errors-<scenario>.json bao gồm:
 *   - summary stats (tổng lỗi theo loại, top URLs lỗi nhiều nhất)
 *   - toàn bộ entries chi tiết
 *
 * @param {string} scenarioName
 * @returns {string} JSON string
 */
export function buildErrorReport(scenarioName) {
  const entries = _errorBuffer;

  // Tổng theo errorType
  const byType = entries.reduce((acc, e) => {
    acc[e.errorType] = (acc[e.errorType] || 0) + 1;
    return acc;
  }, {});

  // Top URLs lỗi nhiều nhất
  const byUrl = entries.reduce((acc, e) => {
    const key = `${e.method} ${e.url}`;
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});

  const topUrls = Object.entries(byUrl)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 10)
    .map(([url, count]) => ({ url, count }));

  const report = {
    scenario:    scenarioName,
    generatedAt: new Date().toISOString(),
    totalErrors: entries.length,
    buffered:    entries.length,
    maxBuffer:   MAX_BUFFER_SIZE,
    byType,
    topErrorUrls: topUrls,
    entries,
  };

  return JSON.stringify(report, null, 2);
}

// ── Internal ─────────────────────────────────────────────────────────────────

/**
 * Phân loại lỗi theo status code.
 * Trả về null nếu status nằm trong expectedStatuses (không phải lỗi).
 *
 * @param {number}   status
 * @param {number[]} expectedStatuses
 * @returns {string|null}
 */
function classifyError(status, expectedStatuses) {
  if (expectedStatuses.includes(status)) return null;

  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 429) return 'rate_limited';
  if (status === 503) return 'circuit_breaker';
  if (status >= 500)  return 'server_error';
  if (status === 0)   return 'network_error';  // timeout / connection refused
  return 'unexpected';
}

function truncate(str, maxLen) {
  if (!str) return '';
  const s = String(str);
  return s.length > maxLen ? s.slice(0, maxLen) + '...' : s;
}

/**
 * Chuẩn hóa URL để tránh cardinality explosion trong InfluxDB.
 * Thay thế các path segment động (UUID, số nguyên) bằng placeholder tĩnh.
 *
 * Ví dụ:
 *   /api/resources/products/123          → /api/resources/products/{id}
 *   /api/admin/routes/auth-login-uuid     → /api/admin/routes/{id}
 *   /api/resources/orders/550e8400-e29b  → /api/resources/orders/{id}
 *
 * @param {string} url
 * @returns {string}
 */
function normalizeUrl(url) {
  if (!url) return 'unknown';
  try {
    // Bỏ query string và fragment
    let path = String(url).split('?')[0].split('#')[0];
    // Bỏ scheme + host (giữ từ path trở đi)
    // http://api-gateway:8080/api/resources/products/123 → /api/resources/products/123
    path = path.replace(/^https?:\/\/[^\/]+/, '');
    // UUID
    path = path.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, '{id}');
    // số nguyên (ID số)
    path = path.replace(/\/\d+(?=\/|$)/g, '/{id}');
    return path || '/';
  } catch (e) {
    return 'unknown';
  }
}
