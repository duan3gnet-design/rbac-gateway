import axios from 'axios'

const api = axios.create({
  baseURL: '/api/auth/admin',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ─── Permission API ────────────────────────────────────────────────────────────

export const permissionApi = {
  /** GET /api/admin/permissions */
  getAll: () => api.get('/permissions'),

  /** GET /api/admin/permissions/:id */
  getById: (id) => api.get(`/permissions/${id}`),

  /** POST /api/admin/permissions */
  create: (data) => api.post('/permissions', data),

  /** PUT /api/admin/permissions/:id */
  update: (id, data) => api.put(`/permissions/${id}`, data),

  /** DELETE /api/admin/permissions/:id */
  remove: (id) => api.delete(`/permissions/${id}`),
}

// ─── User API ─────────────────────────────────────────────────────────────────

export const userApi = {
  /** GET /api/admin/users */
  getAll: () => api.get('/users'),

  /** GET /api/admin/users/:id */
  getById: (id) => api.get(`/users/${id}`),

  /** POST /api/admin/users */
  create: (data) => api.post('/users', data),

  /** PUT /api/admin/users/:id */
  update: (id, data) => api.put(`/users/${id}`, data),

  /** DELETE /api/admin/users/:id */
  remove: (id) => api.delete(`/users/${id}`),

  /** PATCH /api/admin/users/:id/toggle - bật/tắt enabled */
  toggle: (id, enabled) => api.patch(`/users/${id}/toggle`, { enabled }),

  /** GET /api/admin/users/:id/roles */
  getRoles: (id) => api.get(`/users/${id}/roles`),

  /** PUT /api/admin/users/:id/roles - gán roles */
  assignRoles: (id, roles) => api.put(`/users/${id}/roles`, { roles }),
}

export default api
