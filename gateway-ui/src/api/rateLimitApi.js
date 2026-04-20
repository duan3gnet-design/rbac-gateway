import axios from 'axios'

const api = axios.create({
  baseURL: '/api/admin',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * Rate Limit Config API — /api/admin/rate-limits
 */
export const rateLimitApi = {
  /** GET /api/admin/rate-limits */
  getAll: () => api.get('/rate-limits'),

  /** GET /api/admin/rate-limits/default */
  getDefault: () => api.get('/rate-limits/default'),

  /** GET /api/admin/rate-limits/user/:username */
  getByUsername: (username) => api.get(`/rate-limits/user/${encodeURIComponent(username)}`),

  /** GET /api/admin/rate-limits/:id */
  getById: (id) => api.get(`/rate-limits/${id}`),

  /** POST /api/admin/rate-limits */
  create: (data) => api.post('/rate-limits', data),

  /** PUT /api/admin/rate-limits/:id */
  update: (id, data) => api.put(`/rate-limits/${id}`, data),

  /** DELETE /api/admin/rate-limits/:id */
  remove: (id) => api.delete(`/rate-limits/${id}`),
}
