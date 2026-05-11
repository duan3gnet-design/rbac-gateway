/**
 * environment.js — tập trung tất cả config URL và credentials.
 *
 * Override qua biến môi trường khi chạy:
 *   BASE_URL=http://prod-gateway:8080 k6 run ...
 *
 * Auth-service LoginRequest dùng field `username`, không phải `email`.
 * Các biến ADMIN_USERNAME / USER_USERNAME phản ánh đúng tên field này.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const env = {
  baseUrl: BASE_URL,

  // ── Credentials ─────────────────────────────────────────────────────────
  // Dùng ADMIN_USERNAME / USER_USERNAME (khớp với LoginRequest.username)
  adminUsername: __ENV.ADMIN_USERNAME || 'perfAdmin',
  adminPassword: __ENV.ADMIN_PASSWORD || 'Admin@123',
  userUsername:  __ENV.USER_USERNAME  || 'perfUser',
  userPassword:  __ENV.USER_PASSWORD  || 'User@123',

  // Roles để register trong setup()
  adminRoles: ['ROLE_ADMIN'],
  userRoles:  ['ROLE_USER'],

  // ── Endpoints ────────────────────────────────────────────────────────────
  loginUrl:    `${BASE_URL}/api/auth/login`,
  registerUrl: `${BASE_URL}/api/auth/register`,
  refreshUrl:  `${BASE_URL}/api/auth/refresh`,
  productsUrl: `${BASE_URL}/api/resources/products`,
  ordersUrl:   `${BASE_URL}/api/resources/orders`,
  profileUrl:  `${BASE_URL}/api/resources/profile/whoami`,
  adminRoutes: `${BASE_URL}/api/admin/routes`,
  adminPerms:  `${BASE_URL}/api/admin/permissions`,
  actuator:    `${BASE_URL}/actuator/health`,
};
