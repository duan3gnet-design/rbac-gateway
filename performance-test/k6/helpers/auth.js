import http from 'k6/http';
import { check, fail } from 'k6';
import { env } from '../config/environment.js';

/**
 * auth.js — helper login và token management.
 *
 * Auth-service API:
 *   POST /api/auth/register  { username, password, roles: Set<String> }
 *     → 201 created | 400 "Username already exists" | 400 "Role not found: ..."
 *   POST /api/auth/login     { username, password }
 *     → 200 { accessToken, refreshToken } | 401
 */

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * Đăng ký user mới.
 *
 * GlobalExceptionHandler map:
 *   IllegalArgumentException("Username already exists") → 400
 *   IllegalArgumentException("Role not found: ...")    → 400
 *
 * Cả hai đều là 400 — phân biệt qua response body detail.
 *
 * @param {string}   username
 * @param {string}   password
 * @param {string[]} roles  — phải khớp chính xác tên trong DB: "ROLE_ADMIN", "ROLE_USER"
 * @returns {boolean} true nếu register thành công hoặc user đã tồn tại
 */
export function register(username, password, roles) {
  const res = http.post(
    env.registerUrl,
    JSON.stringify({ username, password, roles }),
    { headers: JSON_HEADERS, tags: { name: 'auth/register' } },
  );

  // 201 = user mới tạo → OK
  if (res.status === 201) return true;

  // 400 = IllegalArgumentException — phân biệt qua body
  if (res.status === 400) {
    const detail = safeDetail(res.body);
    if (detail.includes('already exists')) {
      // User đã tồn tại từ lần chạy trước — không phải lỗi
      return true;
    }
    // Role not found hoặc lỗi khác → fail ngay, không thử login
    fail(`[register] FAILED username=${username} status=400 detail="${detail}"`);
  }

  fail(`[register] FAILED username=${username} status=${res.status} body=${res.body}`);
}

/**
 * Đảm bảo user tồn tại rồi login.
 * Gọi trong setup() — chạy 1 lần trước khi VUs bắt đầu.
 *
 * @param {string}   username
 * @param {string}   password
 * @param {string[]} roles
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function ensureAndLogin(username, password, roles) {
  register(username, password, roles);
  return login(username, password);
}

/**
 * Login và trả về { accessToken, refreshToken }.
 *
 * @param {string} username
 * @param {string} password
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function login(username, password) {
  const res = http.post(
    env.loginUrl,
    JSON.stringify({ username, password }),
    { headers: JSON_HEADERS, tags: { name: 'auth/login' } },
  );

  const ok = check(res, {
    'login: status 200':    (r) => r.status === 200,
    'login: có accessToken': (r) => {
      try { return !!JSON.parse(r.body).token; }
      catch { return false; }
    },
  });

  if (!ok) {
    fail(`Login thất bại cho ${username}: status=${res.status} body=${res.body}`);
  }

  const body = JSON.parse(res.body);
  return { accessToken: body.token, refreshToken: body.refreshToken };
}

/**
 * Refresh access token.
 *
 * @param {string} refreshToken
 * @returns {string|null}
 */
export function refreshAccessToken(refreshToken) {
  const res = http.post(
    env.refreshUrl,
    JSON.stringify({ refreshToken: refreshToken }),
    { headers: JSON_HEADERS, tags: { name: 'auth/refresh' } },
  );
  check(res, { 'refresh: status 200': (r) => r.status === 200 });
  try { return JSON.parse(res.body).accessToken; }
  catch { return null; }
}

/**
 * Tạo Authorization + Content-Type headers.
 *
 * @param {string} token
 * @returns {{ Authorization: string, 'Content-Type': string }}
 */
export function authHeaders(token) {
  return {
    Authorization:  `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

// ── helpers ──────────────────────────────────────────────────────────────────

function safeDetail(body) {
  try {
    // ProblemDetail format: { detail: "..." }
    return JSON.parse(body).detail || body || '';
  } catch {
    return body || '';
  }
}
