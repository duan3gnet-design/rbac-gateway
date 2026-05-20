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

export const roleApi = {
  /** GET /api/admin/roles */
  getAll: () => api.get('/roles'),

  /** GET /api/admin/roles/:id */
  getById: (id) => api.get(`/roles/${id}`),

  /** POST /api/admin/roles */
  create: (data) => api.post('/roles', data),

  /** PUT /api/admin/roles/:id */
  update: (id, data) => api.put(`/roles/${id}`, data),

  /** PATCH /api/admin/roles/:id/permissions */
  assignPermissions: (id, permissionIds) =>
    api.patch(`/roles/${id}/permissions`, { permissionIds }),

  /** DELETE /api/admin/roles/:id */
  remove: (id) => api.delete(`/roles/${id}`),
}

export default api
