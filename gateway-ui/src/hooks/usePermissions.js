import { useState, useCallback, useEffect } from 'react'
import { permissionApi } from '../api/userApi'

export function usePermissions() {
  const [permissions, setPermissions] = useState([])
  const [loading, setLoading]         = useState(false)
  const [saving, setSaving]           = useState(false)
  const [error, setError]             = useState(null)

  // ─── Load ──────────────────────────────────────────────────────────────────
  const loadPermissions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await permissionApi.getAll()
      const sorted = [...res.data].sort((a, b) => {
        const roleOrder = a.role.localeCompare(b.role)
        if (roleOrder !== 0) return roleOrder
        const resOrder = a.resource.localeCompare(b.resource)
        if (resOrder !== 0) return resOrder
        return a.action.localeCompare(b.action)
      })
      setPermissions(sorted)
    } catch (e) {
      setError(e.response?.data?.message || e.message || 'Không thể tải danh sách permission')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadPermissions() }, [loadPermissions])

  // ─── Create ────────────────────────────────────────────────────────────────
  const createPermission = useCallback(async (formData) => {
    setSaving(true)
    try {
      const res = await permissionApi.create(formData)
      setPermissions(prev =>
        [...prev, res.data].sort((a, b) => {
          const r = a.role.localeCompare(b.role)
          if (r !== 0) return r
          const res2 = a.resource.localeCompare(b.resource)
          if (res2 !== 0) return res2
          return a.action.localeCompare(b.action)
        })
      )
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi tạo permission')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Update ────────────────────────────────────────────────────────────────
  const updatePermission = useCallback(async (id, formData) => {
    setSaving(true)
    try {
      const res = await permissionApi.update(id, formData)
      setPermissions(prev => prev.map(p => p.id === id ? res.data : p))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi cập nhật permission')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Delete ────────────────────────────────────────────────────────────────
  const deletePermission = useCallback(async (id) => {
    setSaving(true)
    try {
      await permissionApi.remove(id)
      setPermissions(prev => prev.filter(p => p.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi xóa permission')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Derived stats ─────────────────────────────────────────────────────────
  const roleSet     = [...new Set(permissions.map(p => p.role))]
  const resourceSet = [...new Set(permissions.map(p => p.resource))]

  return {
    permissions,
    loading,
    saving,
    error,
    loadPermissions,
    createPermission,
    updatePermission,
    deletePermission,
    roleSet,
    resourceSet,
  }
}
