import { useState, useCallback, useEffect } from 'react'
import { userApi } from '../api/userApi'

export function useUsers() {
  const [users, setUsers]     = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving]   = useState(false)
  const [error, setError]     = useState(null)

  // ─── Load ──────────────────────────────────────────────────────────────────
  const loadUsers = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await userApi.getAll()
      setUsers(res.data)
    } catch (e) {
      setError(e.response?.data?.message || e.message || 'Không thể tải danh sách user')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadUsers() }, [loadUsers])

  // ─── Create ────────────────────────────────────────────────────────────────
  const createUser = useCallback(async (formData) => {
    setSaving(true)
    try {
      const res = await userApi.create(formData)
      setUsers(prev => [...prev, res.data])
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi tạo user')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Update ────────────────────────────────────────────────────────────────
  const updateUser = useCallback(async (id, formData) => {
    setSaving(true)
    try {
      const res = await userApi.update(id, formData)
      setUsers(prev => prev.map(u => u.id === id ? res.data : u))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi cập nhật user')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Delete ────────────────────────────────────────────────────────────────
  const deleteUser = useCallback(async (id) => {
    setSaving(true)
    try {
      await userApi.remove(id)
      setUsers(prev => prev.filter(u => u.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi xóa user')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Toggle enabled ────────────────────────────────────────────────────────
  const toggleUser = useCallback(async (id, enabled) => {
    // Optimistic update
    setUsers(prev => prev.map(u => u.id === id ? { ...u, enabled } : u))
    try {
      const res = await userApi.toggle(id, enabled)
      setUsers(prev => prev.map(u => u.id === id ? res.data : u))
    } catch (e) {
      setUsers(prev => prev.map(u => u.id === id ? { ...u, enabled: !enabled } : u))
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi thay đổi trạng thái user')
    }
  }, [])

  return {
    users,
    loading,
    saving,
    error,
    loadUsers,
    createUser,
    updateUser,
    deleteUser,
    toggleUser,
  }
}
