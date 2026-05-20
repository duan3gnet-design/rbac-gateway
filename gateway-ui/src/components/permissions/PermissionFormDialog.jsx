import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography, Box, Divider,
  Alert, CircularProgress, MenuItem, Select, FormControl,
  InputLabel, Chip, Autocomplete,
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

const DEFAULT_FORM = { role: '', resource: '', action: '', description: '' }

export default function PermissionFormDialog({
  open, onClose, onSave, initialData, saving,
  roleSet = [],       // string[] — roles đang có trong hệ thống
  resources = [],     // [{id, name}] từ DB
  actions = [],       // [{id, name}] từ DB
}) {
  const isEdit = Boolean(initialData?.id)

  const [form, setForm]     = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})

  // Reset form mỗi khi mở dialog
  useEffect(() => {
    if (!open) return
    setErrors({})
    if (initialData) {
      setForm({
        role:        initialData.role        ?? '',
        resource:    initialData.resource    ?? '',
        action:      initialData.action      ?? '',
        description: initialData.description ?? '',
      })
    } else {
      setForm(DEFAULT_FORM)
    }
  }, [open, initialData])

  // ─── Field setters ─────────────────────────────────────────────────────────
  const setField = (field, value) => {
    setForm(f => ({ ...f, [field]: value }))
    if (errors[field]) setErrors(e => ({ ...e, [field]: '' }))
  }

  // ─── Validate ──────────────────────────────────────────────────────────────
  const validate = () => {
    const e = {}
    if (!form.role.trim())     e.role     = 'Role là bắt buộc'
    if (!form.resource.trim()) e.resource = 'Vui lòng chọn resource'
    if (!form.action.trim())   e.action   = 'Vui lòng chọn action'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    onSave({
      role:     form.role.trim(),
      resource: form.resource.trim().toLowerCase(),
      action:   form.action.trim().toUpperCase(),
      code:     `${form.resource.trim().toLowerCase()}:${form.action.trim().toUpperCase()}`,
      description: form.description.trim() || null,
    })
  }

  // ─── Derived ───────────────────────────────────────────────────────────────
  const selectedAction = actions.find(a => a.name === form.action)
  const actionCfg      = ACTION_COLORS[form.action] ?? null
  const previewCode    = form.resource && form.action
    ? `${form.resource.toLowerCase()}:${form.action.toUpperCase()}`
    : null

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>

      {/* ── Title ── */}
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
              Permission nhúng vào JWT dưới dạng <code>resource:ACTION</code>
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      {/* ── Body ── */}
      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={2.5}>

          {/* Role — Autocomplete free-text + chọn từ danh sách có sẵn */}
          <Grid size={12}>
            <Autocomplete
              freeSolo
              options={roleSet}
              value={form.role}
              onChange={(_, v) => setField('role', v ?? '')}
              onInputChange={(_, v) => setField('role', v)}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Role *"
                  size="small"
                  error={Boolean(errors.role)}
                  helperText={errors.role || 'Chọn role có sẵn hoặc nhập role mới (VD: ROLE_ADMIN)'}
                  slotProps={{
                    htmlInput: {
                      ...params.inputProps,
                      style: { fontFamily: "'JetBrains Mono', monospace" },
                    },
                  }}
                />
              )}
              renderOption={(props, option) => {
                const isAdmin = option.toUpperCase().includes('ADMIN')
                return (
                  <li {...props} key={option}>
                    <Chip
                      label={option}
                      size="small"
                      sx={{
                        backgroundColor: isAdmin ? '#f3e8ff' : '#f1f5f9',
                        color: isAdmin ? '#7c3aed' : '#475569',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.72rem',
                        fontWeight: 600,
                        height: 22,
                        '& .MuiChip-label': { px: '8px' },
                      }}
                    />
                  </li>
                )
              }}
            />
          </Grid>

          {/* Resource — Select từ DB */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.resource)}>
              <InputLabel>Resource *</InputLabel>
              <Select
                value={form.resource}
                label="Resource *"
                onChange={e => setField('resource', e.target.value)}
                renderValue={(val) => (
                  <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                    {val}
                  </Typography>
                )}
              >
                {resources.length === 0 ? (
                  <MenuItem disabled>
                    <Typography variant="body2" color="text.disabled">
                      Chưa có resource — hãy tạo trong trang Resources
                    </Typography>
                  </MenuItem>
                ) : (
                  resources.map(r => (
                    <MenuItem key={r.id} value={r.name}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <CategoryIcon sx={{ fontSize: 15, color: '#0284c7' }} />
                        <Typography sx={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          color: '#0f172a',
                        }}>
                          {r.name}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))
                )}
              </Select>
            </FormControl>
            {errors.resource && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5, mt: 0.25, display: 'block' }}>
                {errors.resource}
              </Typography>
            )}
          </Grid>

          {/* Action — Select từ DB */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.action)}>
              <InputLabel>Action *</InputLabel>
              <Select
                value={form.action}
                label="Action *"
                onChange={e => setField('action', e.target.value)}
                renderValue={(val) => {
                  const cfg = ACTION_COLORS[val]
                  return cfg ? (
                    <Chip
                      label={val}
                      size="small"
                      sx={{
                        backgroundColor: cfg.bg,
                        color: cfg.color,
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.72rem',
                        fontWeight: 700,
                        height: 22,
                        '& .MuiChip-label': { px: '8px' },
                      }}
                    />
                  ) : (
                    <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.85rem', fontWeight: 600 }}>
                      {val}
                    </Typography>
                  )
                }}
              >
                {actions.length === 0 ? (
                  <MenuItem disabled>
                    <Typography variant="body2" color="text.disabled">
                      Chưa có action — hãy tạo trong trang Resources
                    </Typography>
                  </MenuItem>
                ) : (
                  actions.map(a => {
                    const cfg = ACTION_COLORS[a.name]
                    return (
                      <MenuItem key={a.id} value={a.name}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                          <BoltIcon sx={{ fontSize: 15, color: cfg?.color ?? '#64748b' }} />
                          {cfg ? (
                            <Chip
                              label={a.name}
                              size="small"
                              sx={{
                                backgroundColor: cfg.bg,
                                color: cfg.color,
                                fontFamily: "'JetBrains Mono', monospace",
                                fontSize: '0.72rem',
                                fontWeight: 700,
                                height: 22,
                                '& .MuiChip-label': { px: '8px' },
                              }}
                            />
                          ) : (
                            <Typography sx={{
                              fontFamily: "'JetBrains Mono', monospace",
                              fontSize: '0.85rem',
                              fontWeight: 600,
                              color: '#0f172a',
                            }}>
                              {a.name}
                            </Typography>
                          )}
                        </Box>
                      </MenuItem>
                    )
                  })
                )}
              </Select>
            </FormControl>
            {errors.action && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5, mt: 0.25, display: 'block' }}>
                {errors.action}
              </Typography>
            )}
          </Grid>

          {/* Preview permission code */}
          {previewCode && (
            <Grid size={12}>
              <Box sx={{
                display: 'flex', alignItems: 'center', gap: 1.5,
                backgroundColor: '#faf5ff',
                border: '1px solid #e9d5ff',
                borderRadius: 2, px: 2, py: 1.25,
              }}>
                <ShieldIcon sx={{ fontSize: 15, color: '#7c3aed', flexShrink: 0 }} />
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                  Permission code:
                </Typography>
                <Typography sx={{
                  fontFamily: "'JetBrains Mono', monospace",
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  color: '#7c3aed',
                  backgroundColor: '#f3e8ff',
                  border: '1px solid #e9d5ff',
                  borderRadius: 1,
                  px: 1, py: 0.25,
                }}>
                  {previewCode}
                </Typography>
                {form.role && (
                  <>
                    <Typography variant="caption" color="text.disabled" sx={{ mx: 0.5 }}>gán cho</Typography>
                    <Chip
                      label={form.role}
                      size="small"
                      sx={{
                        backgroundColor: form.role.toUpperCase().includes('ADMIN') ? '#f3e8ff' : '#f1f5f9',
                        color: form.role.toUpperCase().includes('ADMIN') ? '#7c3aed' : '#475569',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.65rem',
                        fontWeight: 600,
                        height: 20,
                        '& .MuiChip-label': { px: '6px' },
                      }}
                    />
                  </>
                )}
              </Box>
            </Grid>
          )}

          {/* Description */}
          <Grid size={12}>
            <TextField
              label="Mô tả (tùy chọn)"
              value={form.description}
              onChange={e => setField('description', e.target.value)}
              fullWidth
              multiline
              minRows={2}
              size="small"
              placeholder="VD: Cho phép admin đọc danh sách sản phẩm"
            />
          </Grid>

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

      {/* ── Actions ── */}
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
