import axios from 'axios'

const api = axios.create({
  baseURL: '/api/admin',
  headers: { 'Content-Type': 'application/json' },
})

// ─── Interceptor: đính kèm JWT token nếu có ──────────────────────────────────
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ─── Route API ────────────────────────────────────────────────────────────────

export const routeApi = {
  /** GET /api/admin/routes */
  getAll: () => api.get('/routes'),

  /** GET /api/admin/routes/:id */
  getById: (id) => api.get(`/routes/${id}`),

  /** POST /api/admin/routes */
  create: (data) => api.post('/routes', data),

  /** PUT /api/admin/routes/:id */
  update: (id, data) => api.put(`/routes/${id}`, data),

  /** DELETE /api/admin/routes/:id */
  remove: (id) => api.delete(`/routes/${id}`),

  /** PATCH /api/admin/routes/:id/toggle - bật/tắt enabled */
  toggle: (id, enabled) => api.patch(`/routes/${id}/toggle`, { enabled }),

  /** POST /actuator/gateway/refresh - reload routes vào Gateway runtime */
  refreshGateway: () => axios.post('/actuator/gateway/refresh'),
}

// ─── Permission API ───────────────────────────────────────────────────────────

export const permissionApi = {
  /** GET /api/admin/permissions - lấy tất cả permissions */
  getAll: () => api.get('/permissions'),

  /** GET /api/admin/routes/:routeId/permissions */
  getByRoute: (routeId) => api.get(`/routes/${routeId}/permissions`),

  /** PUT /api/admin/routes/:routeId/permissions - gán danh sách permission IDs */
  assign: (routeId, permissionIds) =>
    api.put(`/routes/${routeId}/permissions`, { permissionIds }),
}

export default api

// ─── Eureka API ───────────────────────────────────────────────────────────────

export const eurekaApi = {
  /**
   * GET /api/admin/eureka/services
   * Backend trả về: [{ serviceId, instanceId, homePageUrl, healthCheckUrl, ipAddr, port }]
   */
  getServices: () => api.get('/eureka/services'),
}
