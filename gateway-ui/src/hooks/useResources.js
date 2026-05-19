import { useState, useCallback, useEffect } from 'react'
import { resourceApi, actionApi } from '../api/resourceApi'

export function useResources() {
  const [resources, setResources] = useState([])
  const [actions, setActions]     = useState([])
  const [loading, setLoading]     = useState(false)
  const [saving, setSaving]       = useState(false)
  const [error, setError]         = useState(null)

  // ─── Load ──────────────────────────────────────────────────────────────────
  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [resResult, actResult] = await Promise.all([
        resourceApi.getAll(),
        actionApi.getAll(),
      ])
      setResources(resResult.data.sort((a, b) => a.name.localeCompare(b.name)))
      setActions(actResult.data.sort((a, b) => a.name.localeCompare(b.name)))
    } catch (e) {
      setError(e.response?.data?.detail || e.message || 'Không thể tải dữ liệu')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  // ─── Resource CRUD ─────────────────────────────────────────────────────────
  const createResource = useCallback(async (name) => {
    setSaving(true)
    try {
      const res = await resourceApi.create({ name })
      setResources(prev => [...prev, res.data].sort((a, b) => a.name.localeCompare(b.name)))
      return res.data
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi tạo resource')
    } finally {
      setSaving(false)
    }
  }, [])

  const updateResource = useCallback(async (id, name) => {
    setSaving(true)
    try {
      const res = await resourceApi.update(id, { name })
      setResources(prev =>
        prev.map(r => r.id === id ? res.data : r).sort((a, b) => a.name.localeCompare(b.name))
      )
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi cập nhật resource')
    } finally {
      setSaving(false)
    }
  }, [])

  const deleteResource = useCallback(async (id) => {
    setSaving(true)
    try {
      await resourceApi.remove(id)
      setResources(prev => prev.filter(r => r.id !== id))
    } catch (e) {
      // 409 Conflict — còn permission đang dùng
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi xóa resource')
    } finally {
      setSaving(false)
    }
  }, [])

  // ─── Action CRUD ───────────────────────────────────────────────────────────
  const createAction = useCallback(async (name) => {
    setSaving(true)
    try {
      const res = await actionApi.create(name)
      setActions(prev => [...prev, res.data].sort((a, b) => a.name.localeCompare(b.name)))
      return res.data
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi tạo action')
    } finally {
      setSaving(false)
    }
  }, [])

  const updateAction = useCallback(async (id, name) => {
    setSaving(true)
    try {
      const res = await actionApi.update(id, name)
      setActions(prev =>
        prev.map(a => a.id === id ? res.data : a).sort((a, b) => a.name.localeCompare(b.name))
      )
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi cập nhật action')
    } finally {
      setSaving(false)
    }
  }, [])

  const deleteAction = useCallback(async (id) => {
    setSaving(true)
    try {
      await actionApi.remove(id)
      setActions(prev => prev.filter(a => a.id !== id))
    } catch (e) {
      throw new Error(e.response?.data?.detail || e.message || 'Lỗi khi xóa action')
    } finally {
      setSaving(false)
    }
  }, [])

  return {
    resources, actions,
    loading, saving, error,
    load,
    createResource, updateResource, deleteResource,
    createAction,  updateAction,  deleteAction,
  }
}
