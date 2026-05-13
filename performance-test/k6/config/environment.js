/**
 * environment.js — tập trung tất cả config URL và credentials.
 *
 * Override qua biến môi trường khi chạy:
 *   BASE_URL=http://prod-gateway:8080 k6 run ...
 *
 * Thứ tự ưu tiên credentials:
 *   1. ENV var được truyền từ docker-compose / CLI
 *   2. Default hardcode bên dưới (khớp với docker-compose defaults)
 *
 * Auth-service dùng field `username` (không phải `email`) trong LoginRequest.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const env = {
  baseUrl: BASE_URL,

  // ── Credentials — default khớp với docker-compose.yml K6_* defaults ────
  adminUsername: __ENV.ADMIN_USERNAME || 'k6admin',
  adminPassword: __ENV.ADMIN_PASSWORD || 'Admin@123',
  userUsername:  __ENV.USER_USERNAME  || 'k6user',
  userPassword:  __ENV.USER_PASSWORD  || 'User@123',

  // Roles phải khớp chính xác tên trong bảng roles (migration V1.0__init_database.sql)
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
