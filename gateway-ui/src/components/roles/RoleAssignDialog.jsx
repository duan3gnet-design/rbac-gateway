import { useState, useEffect, useMemo } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, Divider, CircularProgress,
  Chip, TextField, InputAdornment, Checkbox,
  Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow,
} from '@mui/material'
import KeyIcon from '@mui/icons-material/KeyRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

export default function RoleAssignDialog({
  open, onClose, onSave, role, allPermissions, saving,
}) {
  const [selected, setSelected] = useState(new Set())
  const [search, setSearch]     = useState('')

  useEffect(() => {
    if (!open || !role) return
    setSearch('')
    setSelected(new Set((role.permissions ?? []).map(p => p.id)))
  }, [open, role])

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return allPermissions.filter(p =>
      !q ||
      p.code.toLowerCase().includes(q) ||
      p.resource.toLowerCase().includes(q) ||
      p.action.toLowerCase().includes(q)
    )
  }, [allPermissions, search])

  const toggle = (id) =>
    setSelected(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })

  const toggleAll = () => {
    const ids = filtered.map(p => p.id)
    const allChecked = ids.every(id => selected.has(id))
    setSelected(prev => {
      const next = new Set(prev)
      ids.forEach(id => allChecked ? next.delete(id) : next.add(id))
      return next
    })
  }

  const handleSave = () => onSave(role.id, [...selected])

  if (!role) return null

  const filteredIds = filtered.map(p => p.id)
  const allChecked  = filteredIds.length > 0 && filteredIds.every(id => selected.has(id))
  const someChecked = filteredIds.some(id => selected.has(id)) && !allChecked
  const isAdmin     = role.name.includes('ADMIN')

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5, flexShrink: 0,
            background: 'linear-gradient(135deg, #16a34a 0%, #0284c7 100%)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <KeyIcon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="h6">Gán Permissions</Typography>
              <Chip label={role.name} size="small" sx={{
                backgroundColor: isAdmin ? '#f3e8ff' : '#f1f5f9',
                color: isAdmin ? '#7c3aed' : '#475569',
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: '0.72rem', fontWeight: 700, height: 22,
                '& .MuiChip-label': { px: '8px' },
              }} />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              Đã chọn <strong>{selected.size}</strong> / {allPermissions.length} permissions
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ p: 0 }}>
        {/* Search */}
        <Box sx={{ px: 3, py: 1.5, borderBottom: '1px solid #f1f5f9' }}>
          <TextField
            placeholder="Tìm permission (code, resource, action)..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            size="small" fullWidth
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
        </Box>

        <TableContainer sx={{ maxHeight: 400 }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox" sx={{ pl: 2 }}>
                  <Checkbox
                    size="small"
                    checked={allChecked}
                    indeterminate={someChecked}
                    onChange={toggleAll}
                  />
                </TableCell>
                <TableCell>Permission Code</TableCell>
                <TableCell>Resource</TableCell>
                <TableCell>Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" color="text.secondary">
                      Không tìm thấy permission nào
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                filtered.map(perm => {
                  const cfg = ACTION_COLORS[perm.action] ?? { bg: '#f1f5f9', color: '#475569' }
                  const isSelected = selected.has(perm.id)
                  return (
                    <TableRow key={perm.id} hover onClick={() => toggle(perm.id)}
                      sx={{
                        cursor: 'pointer',
                        backgroundColor: isSelected ? '#f0fdf4' : 'transparent',
                        '&:hover': { backgroundColor: isSelected ? '#dcfce7' : '#f8fafc' },
                      }}
                    >
                      <TableCell padding="checkbox" sx={{ pl: 2 }}>
                        <Checkbox size="small" checked={isSelected} color="success"
                          onChange={() => toggle(perm.id)}
                          onClick={e => e.stopPropagation()} />
                      </TableCell>

                      <TableCell>
                        <Typography sx={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: '0.78rem', fontWeight: 700, color: '#475569',
                          backgroundColor: '#f8fafc', border: '1px solid #e2e8f0',
                          borderRadius: 1, px: 1, py: 0.25, display: 'inline-block',
                        }}>
                          {perm.code}
                        </Typography>
                      </TableCell>

                      <TableCell>
                        <Typography sx={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: '0.8rem', fontWeight: 600, color: '#0f172a',
                        }}>
                          {perm.resource}
                        </Typography>
                      </TableCell>

                      <TableCell>
                        <Chip label={perm.action} size="small" sx={{
                          backgroundColor: cfg.bg, color: cfg.color,
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: '0.63rem', fontWeight: 700, height: 20,
                          '& .MuiChip-label': { px: '7px' },
                        }} />
                      </TableCell>
                    </TableRow>
                  )
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
          Đã chọn <strong>{selected.size}</strong> permissions
        </Typography>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button variant="contained" color="success" onClick={handleSave} disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <KeyIcon sx={{ fontSize: 16 }} />}>
          {saving ? 'Đang lưu...' : 'Lưu Permissions'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
