import { useState, useCallback, useEffect } from 'react'
import { roleApi } from '../api/roleApi'
import { permissionApi } from '../api/userApi'

export function useRoles() {
  const [roles, setRoles]           = useState([])
  const [permissions, setPermissions] = useState([]) // tất cả permissions để chọn khi gán
  const [loading, setLoading]       = useState(false)
  const [saving, setSaving]         = useState(false)
  const [error, setError]           = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [rolesRes, permsRes] = await Promise.all([
        roleApi.getAll(),
        permissionApi.getAll(),
      ])
      setRoles(rolesRes.data)
      setPermissions(
        [...permsRes.data].sort((a, b) => {
          return a.code.localeCompare(b.code)
        })
      )
    } catch (e) {
      setError(e.response?.data?.detail || e.message || 'Không thể tải dữ liệu')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // ─── CRUD ──────────────────────────────────────────────────────────────────
  const createRole = useCallback(async (payload) => {
    setSaving(true)
    try {
      const res = await roleApi.create(payload)
      setRoles(prev => [...prev, res.data].sort((a, b) => a.name.localeCompare(b.name)))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi tạo role')
    } finally {
      setSaving(false)
    }
  }, [])

  const updateRole = useCallback(async (id, payload) => {
    setSaving(true)
    try {
      const res = await roleApi.update(id, payload)
      setRoles(prev =>
        prev.map(r => r.id === id ? res.data : r).sort((a, b) => a.name.localeCompare(b.name))
      )
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi cập nhật role')
    } finally {
      setSaving(false)
    }
  }, [])

  const deleteRole = useCallback(async (id) => {
    setSaving(true)
    try {
      await roleApi.remove(id)
      setRoles(prev => prev.filter(r => r.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi xóa role')
    } finally {
      setSaving(false)
    }
  }, [])

  const assignPermissions = useCallback(async (id, permissionIds) => {
    setSaving(true)
    try {
      const res = await roleApi.assignPermissions(id, permissionIds)
      setRoles(prev => prev.map(r => r.id === id ? res.data : r))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi gán permissions')
    } finally {
      setSaving(false)
    }
  }, [])

  return {
    roles, permissions,
    loading, saving, error,
    load,
    createRole, updateRole, deleteRole, assignPermissions,
  }
}
