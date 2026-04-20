import { useState, useCallback, useEffect } from 'react'
import { routeApi, permissionApi } from '../api/routeApi'

/**
 * Hook quản lý toàn bộ state của Route Management.
 * Gọi backend API thật tại /api/admin/* (proxy qua Vite → :8080).
 *
 * Để quay lại mock data (nếu backend chưa chạy):
 *   1. Uncomment import MOCK_ROUTES, MOCK_PERMISSIONS từ '../api/mockData'
 *   2. Thay các API call bằng mock tương ứng (đã comment sẵn)
 */
export function useRoutes() {
  const [routes, setRoutes]           = useState([])
  const [permissions, setPermissions] = useState([])
  const [loading, setLoading]         = useState(false)
  const [saving, setSaving]           = useState(false)
  const [error, setError]             = useState(null)
  const [refreshing, setRefreshing]   = useState(false)

  // ─── Load ──────────────────────────────────────────────────────────────────
  const loadRoutes = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [routeRes, permRes] = await Promise.all([
        routeApi.getAll(),
        permissionApi.getAll(),
      ])
      setRoutes(routeRes.data)
      setPermissions(permRes.data)
    } catch (e) {
      setError(e.response?.data?.message || e.message || 'Không thể tải danh sách route')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadRoutes() }, [loadRoutes])

  // ─── Create ────────────────────────────────────────────────────────────────
  const createRoute = useCallback(async (formData) => {
    setSaving(true)
    try {
      const res = await routeApi.create(formData)
      setRoutes(prev => [...prev, res.data])
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi tạo route')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Update ────────────────────────────────────────────────────────────────
  const updateRoute = useCallback(async (id, formData) => {
    setSaving(true)
    try {
      const res = await routeApi.update(id, formData)
      setRoutes(prev => prev.map(r => r.id === id ? res.data : r))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi cập nhật route')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Delete ────────────────────────────────────────────────────────────────
  const deleteRoute = useCallback(async (id) => {
    setSaving(true)
    try {
      await routeApi.remove(id)
      setRoutes(prev => prev.filter(r => r.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi xóa route')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Toggle ────────────────────────────────────────────────────────────────
  const toggleRoute = useCallback(async (id, enabled) => {
    // Optimistic update
    setRoutes(prev => prev.map(r => r.id === id ? { ...r, enabled } : r))
    try {
      const res = await routeApi.toggle(id, enabled)
      setRoutes(prev => prev.map(r => r.id === id ? res.data : r))
    } catch (e) {
      // Rollback khi lỗi
      setRoutes(prev => prev.map(r => r.id === id ? { ...r, enabled: !enabled } : r))
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi toggle route')
    }
  }, [])

  // ─── Assign permissions ────────────────────────────────────────────────────
  const assignPermissions = useCallback(async (routeId, permissionIds) => {
    setSaving(true)
    try {
      const res = await permissionApi.assign(routeId, permissionIds)
      // Backend trả về danh sách permissionIds mới
      setRoutes(prev =>
        prev.map(r => r.id === routeId ? { ...r, permissionIds: res.data } : r)
      )
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi lưu phân quyền')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Refresh gateway ───────────────────────────────────────────────────────
  const refreshGateway = useCallback(async () => {
    setRefreshing(true)
    try {
      await routeApi.refreshGateway()
      return true
    } catch {
      return false
    } finally {
      setRefreshing(false)
    }
  }, [])

  return {
    routes,
    permissions,
    loading,
    saving,
    error,
    refreshing,
    loadRoutes,
    createRoute,
    updateRoute,
    deleteRoute,
    toggleRoute,
    assignPermissions,
    refreshGateway,
  }
}

// ─── Parse helpers ─────────────────────────────────────────────────────────
/** Parse predicate JSON → trích xuất path pattern + HTTP methods */
export function parsePredicates(predicatesJson) {
  try {
    const arr = JSON.parse(predicatesJson || '[]')
    const path   = arr.find(p => p.name === 'Path')?.args?.pattern   ?? '—'
    const method = arr.find(p => p.name === 'Method')?.args?.methods ?? 'ANY'
    return { path, methods: method.split(',').map(m => m.trim()) }
  } catch {
    return { path: predicatesJson ?? '—', methods: ['ANY'] }
  }
}

/** Parse filter JSON → trả về tên các filter */
export function parseFilters(filtersJson) {
  try {
    const arr = JSON.parse(filtersJson || '[]')
    return arr.map(f => f.name).filter(Boolean)
  } catch {
    return []
  }
}

/** Trích xuất hostname từ URI */
export function parseService(uri) {
  try {
    return new URL(uri).host
  } catch {
    return uri
  }
}
