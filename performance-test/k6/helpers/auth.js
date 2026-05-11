import http from 'k6/http';
import { check, fail } from 'k6';
import { env } from '../config/environment.js';

/**
 * auth.js — helper login và token management.
 *
 * Auth-service dùng field `username` (không phải `email`).
 * LoginRequest: { username: string, password: string }
 * RegisterRequest: { username: string, password: string, roles: string[] }
 */

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * Đăng ký user mới. Bỏ qua lỗi 409 (user đã tồn tại).
 * Gọi trong setup() trước khi login.
 *
 * @param {string} username
 * @param {string} password
 * @param {string[]} roles  - ví dụ: ['ROLE_ADMIN'] hoặc ['ROLE_USER']
 */
export function register(username, password, roles) {
  const res = http.post(
    env.registerUrl,
    JSON.stringify({ username, password, roles }),
    { headers: JSON_HEADERS, tags: { name: 'auth/register' } },
  );

  // 201 = tạo mới, 409 = đã tồn tại → cả hai đều OK cho setup
  const ok = [201, 409].includes(res.status);
  if (!ok) {
    console.warn(`[register] username=${username} status=${res.status} body=${res.body}`);
  }
  return ok;
}

/**
 * Login và trả về { accessToken, refreshToken }.
 * Gọi trong setup() để chỉ login 1 lần cho toàn bộ test.
 *
 * @param {string} username  - field `username` (không phải email)
 * @param {string} password
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function login(username, password) {
  const res = http.post(
    env.loginUrl,
    JSON.stringify({ username, password }),   // ← auth-service dùng "username"
    { headers: JSON_HEADERS, tags: { name: 'auth/login' } },
  );

  const ok = check(res, {
    'login: status 200': (r) => r.status === 200,
    'login: có accessToken': (r) => {
      try { return !!JSON.parse(r.body).token; }
      catch { return false; }
    },
  });

  if (!ok) {
    fail(`Login thất bại cho ${username}: status=${res.status} body=${res.body}`);
  }

  const body = JSON.parse(res.body);
  return {
    accessToken:  body.token,
    refreshToken: body.refreshToken,
  };
}

/**
 * Ensure user tồn tại rồi login — tiện dùng trong setup().
 * Register trước (bỏ qua nếu đã có), sau đó login.
 *
 * @param {string} username
 * @param {string} password
 * @param {string[]} roles
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function ensureAndLogin(username, password, roles) {
  register(username, password, roles);
  return login(username, password);
}

/**
 * Refresh token và trả về accessToken mới.
 *
 * @param {string} refreshToken
 * @returns {string|null} accessToken mới
 */
export function refreshAccessToken(refreshToken) {
  const res = http.post(
    env.refreshUrl,
    JSON.stringify({ refreshToken }),
    { headers: JSON_HEADERS, tags: { name: 'auth/refresh' } },
  );

  check(res, { 'refresh: status 200': (r) => r.status === 200 });

  try {
    return JSON.parse(res.body).accessToken;
  } catch {
    return null;
  }
}

/**
 * Tạo Authorization + Content-Type headers từ token.
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
