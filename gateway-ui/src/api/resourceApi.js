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

// ─── Resource API ─────────────────────────────────────────────────────────────

export const resourceApi = {
  /** GET /api/admin/resources */
  getAll: () => api.get('/resources'),

  /** GET /api/admin/resources/:id */
  getById: (id) => api.get(`/resources/${id}`),

  /** POST /api/admin/resources */
  create: (data) => api.post('/resources', data),

  /** PUT /api/admin/resources/:id */
  update: (id, data) => api.put(`/resources/${id}`, data),

  /** DELETE /api/admin/resources/:id */
  remove: (id) => api.delete(`/resources/${id}`),
}

// ─── Action API ───────────────────────────────────────────────────────────────

export const actionApi = {
  /** GET /api/admin/resources/actions */
  getAll: () => api.get('/resources/actions'),

  /** POST /api/admin/resources/actions */
  create: (name) => api.post('/resources/actions', { name }),

  /** PUT /api/admin/resources/actions/:id */
  update: (id, name) => api.put(`/resources/actions/${id}`, { name }),

  /** DELETE /api/admin/resources/actions/:id */
  remove: (id) => api.delete(`/resources/actions/${id}`),
}

export default api
