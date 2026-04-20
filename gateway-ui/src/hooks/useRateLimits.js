import { useState, useCallback, useEffect } from 'react'
import { rateLimitApi } from '../api/rateLimitApi'

export function useRateLimits() {
  const [configs, setConfigs]   = useState([])
  const [loading, setLoading]   = useState(false)
  const [saving, setSaving]     = useState(false)
  const [error, setError]       = useState(null)

  // ─── Load ──────────────────────────────────────────────────────────────────
  const loadConfigs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await rateLimitApi.getAll()
      // Global default (username === null) luôn đứng đầu
      const sorted = [...res.data].sort((a, b) => {
        if (a.username === null) return -1
        if (b.username === null) return 1
        return a.username.localeCompare(b.username)
      })
      setConfigs(sorted)
    } catch (e) {
      setError(e.response?.data?.message || e.message || 'Không thể tải cấu hình rate limit')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadConfigs() }, [loadConfigs])

  // ─── Create / Update global default ───────────────────────────────────────
  const createConfig = useCallback(async (formData) => {
    setSaving(true)
    try {
      const res = await rateLimitApi.create(formData)
      // create với username=null sẽ update global default → replace thay vì push
      setConfigs(prev => {
        const incoming = res.data
        const exists = prev.some(c => c.id === incoming.id)
        if (exists) return prev.map(c => c.id === incoming.id ? incoming : c)
        const sorted = [...prev, incoming].sort((a, b) => {
          if (a.username === null) return -1
          if (b.username === null) return 1
          return a.username.localeCompare(b.username)
        })
        return sorted
      })
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi tạo cấu hình')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Update ────────────────────────────────────────────────────────────────
  const updateConfig = useCallback(async (id, formData) => {
    setSaving(true)
    try {
      const res = await rateLimitApi.update(id, formData)
      setConfigs(prev => prev.map(c => c.id === id ? res.data : c))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi cập nhật cấu hình')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Delete ────────────────────────────────────────────────────────────────
  const deleteConfig = useCallback(async (id) => {
    setSaving(true)
    try {
      await rateLimitApi.remove(id)
      setConfigs(prev => prev.filter(c => c.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi xóa cấu hình')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Toggle enabled ────────────────────────────────────────────────────────
  const toggleConfig = useCallback(async (id, enabled) => {
    setConfigs(prev => prev.map(c => c.id === id ? { ...c, enabled } : c))
    try {
      const res = await rateLimitApi.update(id, { enabled })
      setConfigs(prev => prev.map(c => c.id === id ? res.data : c))
    } catch (e) {
      setConfigs(prev => prev.map(c => c.id === id ? { ...c, enabled: !enabled } : c))
      throw new Error(e.response?.data?.message || e.message || 'Lỗi khi toggle cấu hình')
    }
  }, [])

  return {
    configs,
    loading,
    saving,
    error,
    loadConfigs,
    createConfig,
    updateConfig,
    deleteConfig,
    toggleConfig,
  }
}
