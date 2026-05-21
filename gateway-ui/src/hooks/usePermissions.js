import { useState, useCallback, useEffect } from 'react'
import { permissionApi } from '../api/userApi'
import { resourceApi, actionApi } from '../api/resourceApi'

export function usePermissions() {
  const [permissions, setPermissions] = useState([])
  const [resources, setResources]     = useState([])
  const [actions, setActions]         = useState([])
  const [loading, setLoading]         = useState(false)
  const [saving, setSaving]           = useState(false)
  const [error, setError]             = useState(null)

  const loadPermissions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [permRes, resRes, actRes] = await Promise.all([
        permissionApi.getAll(),
        resourceApi.getAll(),
        actionApi.getAll(),
      ])
      const sorted = [...permRes.data].sort((a, b) => {
        const r = a.resource.localeCompare(b.resource)
        return r !== 0 ? r : a.action.localeCompare(b.action)
      })
      setPermissions(sorted)
      setResources(resRes.data.sort((a, b) => a.name.localeCompare(b.name)))
      setActions(actRes.data.sort((a, b) => a.name.localeCompare(b.name)))
    } catch (e) {
      setError(e.response?.data?.detail || e.response?.data?.message || e.message || 'Không thể tải dữ liệu')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadPermissions() }, [loadPermissions])

  const createPermission = useCallback(async (formData) => {
    setSaving(true)
    try {
      const res = await permissionApi.create(formData)
      setPermissions(prev =>
        [...prev, res.data].sort((a, b) => {
          const r = a.resource.localeCompare(b.resource)
          return r !== 0 ? r : a.action.localeCompare(b.action)
        })
      )
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.response?.data?.message || e.message || 'Lỗi khi tạo permission')
    } finally {
      setSaving(false)
    }
  }, [])

  const updatePermission = useCallback(async (id, formData) => {
    setSaving(true)
    try {
      const res = await permissionApi.update(id, formData)
      setPermissions(prev => prev.map(p => p.id === id ? res.data : p))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.response?.data?.message || e.message || 'Lỗi khi cập nhật permission')
    } finally {
      setSaving(false)
    }
  }, [])

  const deletePermission = useCallback(async (id) => {
    setSaving(true)
    try {
      await permissionApi.remove(id)
      setPermissions(prev => prev.filter(p => p.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.response?.data?.message || e.message || 'Lỗi khi xóa permission')
    } finally {
      setSaving(false)
    }
  }, [])

  return {
    permissions,
    resources,
    actions,
    loading,
    saving,
    error,
    loadPermissions,
    createPermission,
    updatePermission,
    deletePermission,
  }
}
