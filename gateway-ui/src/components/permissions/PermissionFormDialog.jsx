import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography, Box, Divider,
  Alert, CircularProgress, MenuItem, Select, FormControl,
  InputLabel, Chip,
} from '@mui/material'
import ShieldIcon from '@mui/icons-material/ShieldRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

const DEFAULT_FORM = { resource: '', action: '' }

export default function PermissionFormDialog({
  open, onClose, onSave, initialData, saving,
  resources = [],
  actions   = [],
}) {
  const isEdit = Boolean(initialData?.id)
  const [form, setForm]     = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})

  useEffect(() => {
    if (!open) return
    setErrors({})
    setForm(initialData
      ? { resource: initialData.resource ?? '', action: initialData.action ?? '' }
      : DEFAULT_FORM
    )
  }, [open, initialData])

  const setField = (field, value) => {
    setForm(f => ({ ...f, [field]: value }))
    if (errors[field]) setErrors(e => ({ ...e, [field]: '' }))
  }

  const validate = () => {
    const e = {}
    if (!form.resource) e.resource = 'Vui lòng chọn resource'
    if (!form.action)   e.action   = 'Vui lòng chọn action'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    onSave({ resource: form.resource, action: form.action })
  }

  const previewCode = form.resource && form.action
    ? `${form.resource}:${form.action}`
    : null

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5, flexShrink: 0,
            background: 'linear-gradient(135deg, #7c3aed 0%, #a855f7 100%)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <ShieldIcon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Typography variant="h6">
              {isEdit ? 'Sửa Permission' : 'Thêm Permission'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              Gán cho role qua trang <strong>Roles</strong>
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={2.5}>

          {/* Resource */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.resource)}>
              <InputLabel>Resource *</InputLabel>
              <Select
                value={form.resource}
                label="Resource *"
                onChange={e => setField('resource', e.target.value)}
                renderValue={val => (
                  <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                    {val}
                  </Typography>
                )}
              >
                {resources.length === 0 ? (
                  <MenuItem disabled>Chưa có resource — tạo trong trang Resources</MenuItem>
                ) : resources.map(r => (
                  <MenuItem key={r.id} value={r.name}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <CategoryIcon sx={{ fontSize: 15, color: '#0284c7' }} />
                      <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                        {r.name}
                      </Typography>
                    </Box>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {errors.resource && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5, mt: 0.25, display: 'block' }}>
                {errors.resource}
              </Typography>
            )}
          </Grid>

          {/* Action */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.action)}>
              <InputLabel>Action *</InputLabel>
              <Select
                value={form.action}
                label="Action *"
                onChange={e => setField('action', e.target.value)}
                renderValue={val => {
                  const cfg = ACTION_COLORS[val]
                  return cfg
                    ? <Chip label={val} size="small" sx={{
                        backgroundColor: cfg.bg, color: cfg.color,
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.72rem', fontWeight: 700, height: 22,
                        '& .MuiChip-label': { px: '8px' },
                      }} />
                    : <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                        {val}
                      </Typography>
                }}
              >
                {actions.length === 0 ? (
                  <MenuItem disabled>Chưa có action — tạo trong trang Resources</MenuItem>
                ) : actions.map(a => {
                  const cfg = ACTION_COLORS[a.name]
                  return (
                    <MenuItem key={a.id} value={a.name}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <BoltIcon sx={{ fontSize: 15, color: cfg?.color ?? '#64748b' }} />
                        {cfg
                          ? <Chip label={a.name} size="small" sx={{
                              backgroundColor: cfg.bg, color: cfg.color,
                              fontFamily: "'JetBrains Mono', monospace",
                              fontSize: '0.72rem', fontWeight: 700, height: 22,
                              '& .MuiChip-label': { px: '8px' },
                            }} />
                          : <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                              {a.name}
                            </Typography>
                        }
                      </Box>
                    </MenuItem>
                  )
                })}
              </Select>
            </FormControl>
            {errors.action && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5, mt: 0.25, display: 'block' }}>
                {errors.action}
              </Typography>
            )}
          </Grid>

          {/* Preview */}
          {previewCode && (
            <Grid size={12}>
              <Box sx={{
                display: 'flex', alignItems: 'center', gap: 1.5,
                backgroundColor: '#faf5ff', border: '1px solid #e9d5ff',
                borderRadius: 2, px: 2, py: 1.25,
              }}>
                <ShieldIcon sx={{ fontSize: 15, color: '#7c3aed', flexShrink: 0 }} />
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  Permission code:
                </Typography>
                <Typography sx={{
                  fontFamily: "'JetBrains Mono', monospace",
                  fontWeight: 700, fontSize: '0.85rem', color: '#7c3aed',
                  backgroundColor: '#f3e8ff', border: '1px solid #e9d5ff',
                  borderRadius: 1, px: 1, py: 0.25,
                }}>
                  {previewCode}
                </Typography>
              </Box>
            </Grid>
          )}

          {Object.keys(errors).length > 0 && (
            <Grid size={12}>
              <Alert severity="error" sx={{ borderRadius: 2 }}>
                Vui lòng điền đầy đủ các trường bắt buộc.
              </Alert>
            </Grid>
          )}
        </Grid>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={saving || resources.length === 0 || actions.length === 0}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo Permission'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
