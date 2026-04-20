import { useState, useEffect } from 'react'
import {
  Drawer, Box, Typography, Divider, IconButton,
  Checkbox, FormControlLabel, Button, CircularProgress,
  Chip, TextField, InputAdornment, Alert,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import ShieldIcon from '@mui/icons-material/ShieldRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import SaveIcon from '@mui/icons-material/SaveRounded'

const ROLE_COLORS = {
  ROLE_ADMIN: { bg: '#ede9fe', color: '#7c3aed' },
  ROLE_USER:  { bg: '#e0f2fe', color: '#0284c7' },
}

export default function RoutePermissionPanel({ open, onClose, route, permissions, onSave, saving }) {
  const [selected, setSelected] = useState(new Set())
  const [search, setSearch]     = useState('')
  const [dirty, setDirty]       = useState(false)

  useEffect(() => {
    if (open && route) {
      setSelected(new Set(route.permissionIds ?? []))
      setSearch('')
      setDirty(false)
    }
  }, [open, route])

  if (!route) return null

  const toggle = (id) => {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
    setDirty(true)
  }

  // Group permissions by role
  const filtered = permissions.filter(p =>
    p.code.toLowerCase().includes(search.toLowerCase()) ||
    p.role.toLowerCase().includes(search.toLowerCase())
  )
  const grouped = filtered.reduce((acc, p) => {
    ;(acc[p.role] = acc[p.role] ?? []).push(p)
    return acc
  }, {})

  const handleSave = () => onSave(route.id, [...selected])

  const selectAllInGroup = (perms) => {
    setSelected(prev => {
      const next = new Set(prev)
      perms.forEach(p => next.add(p.id))
      return next
    })
    setDirty(true)
  }

  const clearGroup = (perms) => {
    setSelected(prev => {
      const next = new Set(prev)
      perms.forEach(p => next.delete(p.id))
      return next
    })
    setDirty(true)
  }

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: 420, backgroundColor: '#fff', display: 'flex', flexDirection: 'column' },
      }}
    >
      {/* Header */}
      <Box
        sx={{
          px: 3, py: 2.5,
          background: 'linear-gradient(135deg, #3b5bdb 0%, #7c3aed 100%)',
          display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
        }}
      >
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            <ShieldIcon sx={{ color: 'rgba(255,255,255,0.9)', fontSize: 20 }} />
            <Typography variant="h6" sx={{ color: '#fff', fontWeight: 700 }}>
              Phân quyền Route
            </Typography>
          </Box>
          <Typography
            variant="body2"
            sx={{
              color: 'rgba(255,255,255,0.75)',
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: '0.78rem',
            }}
          >
            {route.id}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose} sx={{ color: 'rgba(255,255,255,0.8)', mt: -0.5 }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* Search */}
      <Box sx={{ px: 2.5, pt: 2, pb: 1 }}>
        <TextField
          placeholder="Tìm permission..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          fullWidth
          size="small"
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon sx={{ fontSize: 16, color: '#94a3b8' }} />
                </InputAdornment>
              ),
            },
          }}
        />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1.5, alignItems: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            Đã chọn <strong>{selected.size}</strong> / {permissions.length} permissions
          </Typography>
          <Button
            size="small"
            sx={{ fontSize: '0.72rem', color: '#64748b' }}
            onClick={() => { setSelected(new Set()); setDirty(true) }}
          >
            Bỏ chọn tất cả
          </Button>
        </Box>
      </Box>

      <Divider />

      {/* Permission list grouped by role */}
      <Box sx={{ flex: 1, overflow: 'auto', px: 2.5, py: 1.5 }}>
        {Object.keys(grouped).length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Typography variant="body2" color="text.secondary">
              Không tìm thấy permission nào
            </Typography>
          </Box>
        ) : (
          Object.entries(grouped).map(([role, perms]) => {
            const roleColor = ROLE_COLORS[role] ?? { bg: '#f1f5f9', color: '#475569' }
            const allChecked = perms.every(p => selected.has(p.id))
            return (
              <Box key={role} sx={{ mb: 2.5 }}>
                {/* Role header */}
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Chip
                    label={role}
                    size="small"
                    sx={{
                      backgroundColor: roleColor.bg,
                      color: roleColor.color,
                      fontWeight: 700,
                      fontSize: '0.7rem',
                      height: 22,
                    }}
                  />
                  <Button
                    size="small"
                    sx={{ fontSize: '0.68rem', color: '#64748b', minWidth: 0 }}
                    onClick={() => allChecked ? clearGroup(perms) : selectAllInGroup(perms)}
                  >
                    {allChecked ? 'Bỏ chọn nhóm' : 'Chọn nhóm'}
                  </Button>
                </Box>

                {/* Permission checkboxes */}
                <Box
                  sx={{
                    border: '1px solid #e2e8f0',
                    borderRadius: 2,
                    overflow: 'hidden',
                  }}
                >
                  {perms.map((p, idx) => (
                    <Box
                      key={p.id}
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        px: 1.5,
                        py: 0.75,
                        borderBottom: idx < perms.length - 1 ? '1px solid #f1f5f9' : 'none',
                        backgroundColor: selected.has(p.id) ? '#f5f7ff' : '#fff',
                        cursor: 'pointer',
                        transition: 'background 0.12s',
                        '&:hover': { backgroundColor: selected.has(p.id) ? '#eef1ff' : '#f8fafc' },
                      }}
                      onClick={() => toggle(p.id)}
                    >
                      <Checkbox
                        checked={selected.has(p.id)}
                        onChange={() => toggle(p.id)}
                        size="small"
                        sx={{ p: 0.5, mr: 1, color: '#cbd5e1', '&.Mui-checked': { color: '#3b5bdb' } }}
                        onClick={e => e.stopPropagation()}
                      />
                      <Box sx={{ flex: 1 }}>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: "'JetBrains Mono', monospace",
                            fontSize: '0.78rem',
                            fontWeight: selected.has(p.id) ? 600 : 400,
                            color: selected.has(p.id) ? '#3b5bdb' : '#0f172a',
                          }}
                        >
                          {p.code}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {p.resource} · {p.action}
                        </Typography>
                      </Box>
                      {selected.has(p.id) && (
                        <Box
                          sx={{
                            width: 6, height: 6, borderRadius: '50%',
                            backgroundColor: '#3b5bdb', flexShrink: 0,
                          }}
                        />
                      )}
                    </Box>
                  ))}
                </Box>
              </Box>
            )
          })
        )}
      </Box>

      <Divider />

      {/* Footer actions */}
      <Box sx={{ px: 2.5, py: 2 }}>
        {dirty && (
          <Alert severity="info" sx={{ mb: 1.5, borderRadius: 2, py: 0.5, fontSize: '0.78rem' }}>
            Có thay đổi chưa được lưu
          </Alert>
        )}
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button onClick={onClose} color="inherit" fullWidth disabled={saving}>
            Đóng
          </Button>
          <Button
            variant="contained"
            fullWidth
            onClick={handleSave}
            disabled={saving || !dirty}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon sx={{ fontSize: 16 }} />}
          >
            {saving ? 'Đang lưu...' : 'Lưu phân quyền'}
          </Button>
        </Box>
      </Box>
    </Drawer>
  )
}
