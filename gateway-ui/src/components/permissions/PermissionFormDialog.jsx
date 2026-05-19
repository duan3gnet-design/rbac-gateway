import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography, Box, Divider,
  Alert, CircularProgress, MenuItem, Select, FormControl,
  InputLabel, Chip,
} from '@mui/material'
import ShieldIcon from '@mui/icons-material/ShieldRounded'

const COMMON_ACTIONS = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'WRITE', 'MANAGE']

const DEFAULT_FORM = {
  role: '',
  resource: '',
  action: '',
  description: '',
}

export default function PermissionFormDialog({
  open, onClose, onSave, initialData, saving, roleSet = [], resourceSet = [],
}) {
  const isEdit = Boolean(initialData?.id)
  const [form, setForm]     = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})

  useEffect(() => {
    if (open) {
      if (initialData) {
        setForm({
          role:        initialData.role ?? '',
          resource:    initialData.resource ?? '',
          action:      initialData.action ?? '',
          description: initialData.description ?? '',
        })
      } else {
        setForm(DEFAULT_FORM)
      }
      setErrors({})
    }
  }, [open, initialData])

  const set = (field) => (e) => {
    const value = e?.target !== undefined ? e.target.value : e
    setForm(f => ({ ...f, [field]: value }))
    if (errors[field]) setErrors(er => ({ ...er, [field]: '' }))
  }

  const validate = () => {
    const e = {}
    if (!form.role.trim())     e.role     = 'Role là bắt buộc'
    if (!form.resource.trim()) e.resource = 'Resource là bắt buộc'
    if (!form.action.trim())   e.action   = 'Action là bắt buộc'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    const code = `${form.resource.trim().toLowerCase()}:${form.action.trim().toUpperCase()}`
    onSave({
      role:        form.role.trim(),
      resource:    form.resource.trim().toLowerCase(),
      action:      form.action.trim().toUpperCase(),
      code,
      description: form.description.trim() || null,
    })
  }

  const previewCode = form.resource && form.action
    ? `${form.resource.trim().toLowerCase()}:${form.action.trim().toUpperCase()}`
    : '—'

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 32, height: 32, borderRadius: 1.5,
              background: 'linear-gradient(135deg, #7c3aed 0%, #a855f7 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <ShieldIcon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Typography variant="h6">
              {isEdit ? 'Sửa Permission' : 'Thêm Permission'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              Permission được nhúng vào JWT dưới dạng <code>resource:ACTION</code>
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={2.5}>

          {/* Role */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.role)}>
              <InputLabel>Role *</InputLabel>
              <Select
                value={form.role}
                label="Role *"
                onChange={set('role')}
              >
                {roleSet.map(r => (
                  <MenuItem key={r} value={r}>{r}</MenuItem>
                ))}
                {/* Cho phép nhập tự do nếu chưa có trong danh sách */}
              </Select>
            </FormControl>
            {errors.role && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5 }}>{errors.role}</Typography>
            )}
            <TextField
              label="Hoặc nhập role mới"
              value={form.role}
              onChange={set('role')}
              size="small"
              fullWidth
              sx={{ mt: 1 }}
              placeholder="VD: ROLE_ADMIN, ROLE_USER, ROLE_MODERATOR"
              helperText="Chọn từ danh sách hoặc nhập role mới ở đây"
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } } }}
            />
          </Grid>

          {/* Resource */}
          <Grid size={12}>
            <TextField
              label="Resource *"
              value={form.resource}
              onChange={set('resource')}
              error={Boolean(errors.resource)}
              helperText={errors.resource || 'Tên resource (lowercase), VD: products, orders, users'}
              fullWidth
              size="small"
              placeholder="products"
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } } }}
            />
            {resourceSet.length > 0 && (
              <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.75 }}>
                <Typography variant="caption" color="text.disabled" sx={{ alignSelf: 'center', mr: 0.5 }}>
                  Gợi ý:
                </Typography>
                {resourceSet.map(r => (
                  <Chip
                    key={r}
                    label={r}
                    size="small"
                    onClick={() => set('resource')({ target: { value: r } })}
                    sx={{
                      height: 20,
                      fontSize: '0.65rem',
                      cursor: 'pointer',
                      backgroundColor: form.resource === r ? '#eef2ff' : '#f1f5f9',
                      color: form.resource === r ? '#3b5bdb' : '#64748b',
                      fontFamily: "'JetBrains Mono', monospace",
                      '& .MuiChip-label': { px: '6px' },
                    }}
                  />
                ))}
              </Box>
            )}
          </Grid>

          {/* Action */}
          <Grid size={12}>
            <FormControl fullWidth size="small" error={Boolean(errors.action)}>
              <InputLabel>Action *</InputLabel>
              <Select
                value={form.action}
                label="Action *"
                onChange={set('action')}
              >
                {COMMON_ACTIONS.map(a => (
                  <MenuItem key={a} value={a}>
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 600 }}
                    >
                      {a}
                    </Typography>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {errors.action && (
              <Typography variant="caption" color="error" sx={{ ml: 1.5 }}>{errors.action}</Typography>
            )}
          </Grid>

          {/* Preview code */}
          {form.resource || form.action ? (
            <Grid size={12}>
              <Box
                sx={{
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 2, px: 2, py: 1.5,
                  display: 'flex', alignItems: 'center', gap: 1.5,
                }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  Permission Code:
                </Typography>
                <Typography
                  variant="body2"
                  sx={{
                    fontFamily: "'JetBrains Mono', monospace",
                    fontWeight: 700,
                    color: '#7c3aed',
                    backgroundColor: '#f3e8ff',
                    border: '1px solid #e9d5ff',
                    borderRadius: 1,
                    px: 1, py: 0.25,
                  }}
                >
                  {previewCode}
                </Typography>
              </Box>
            </Grid>
          ) : null}

          {/* Description */}
          <Grid size={12}>
            <TextField
              label="Mô tả (tùy chọn)"
              value={form.description}
              onChange={set('description')}
              fullWidth
              multiline
              minRows={2}
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
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo Permission'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
