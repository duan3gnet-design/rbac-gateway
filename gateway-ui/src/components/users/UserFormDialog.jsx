import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography, Box, Divider,
  Alert, CircularProgress, Checkbox, FormControlLabel,
  Chip, FormGroup, Switch,
} from '@mui/material'
import PeopleIcon from '@mui/icons-material/PeopleRounded'

const COMMON_ROLES = ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_MODERATOR', 'ROLE_MANAGER']

const DEFAULT_FORM = {
  username:  '',
  email:     '',
  fullName:  '',
  password:  '',
  roles:     ['ROLE_USER'],
  enabled:   true,
}

export default function UserFormDialog({
  open, onClose, onSave, initialData, saving,
}) {
  const isEdit = Boolean(initialData?.id)
  const [form, setForm]     = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})
  const [customRole, setCustomRole] = useState('')

  useEffect(() => {
    if (open) {
      if (initialData) {
        setForm({
          username:  initialData.username ?? '',
          email:     initialData.email ?? '',
          fullName:  initialData.fullName ?? '',
          password:  '',
          roles:     initialData.roles ?? ['ROLE_USER'],
          enabled:   initialData.enabled ?? true,
        })
      } else {
        setForm(DEFAULT_FORM)
      }
      setErrors({})
      setCustomRole('')
    }
  }, [open, initialData])

  const set = (field) => (e) => {
    const value = e?.target !== undefined
      ? (e.target.type === 'checkbox' ? e.target.checked : e.target.value)
      : e
    setForm(f => ({ ...f, [field]: value }))
    if (errors[field]) setErrors(er => ({ ...er, [field]: '' }))
  }

  const toggleRole = (role) => {
    setForm(f => {
      const has = f.roles.includes(role)
      return {
        ...f,
        roles: has ? f.roles.filter(r => r !== role) : [...f.roles, role],
      }
    })
  }

  const addCustomRole = () => {
    const r = customRole.trim().toUpperCase()
    if (!r) return
    const normalized = r.startsWith('ROLE_') ? r : `ROLE_${r}`
    if (!form.roles.includes(normalized)) {
      setForm(f => ({ ...f, roles: [...f.roles, normalized] }))
    }
    setCustomRole('')
  }

  const validate = () => {
    const e = {}
    if (!form.username.trim()) e.username = 'Username là bắt buộc'
    if (!form.email.trim())    e.email    = 'Email là bắt buộc'
    else if (!/\S+@\S+\.\S+/.test(form.email)) e.email = 'Email không hợp lệ'
    if (!isEdit && !form.password.trim()) e.password = 'Password là bắt buộc khi tạo mới'
    if (form.roles.length === 0) e.roles = 'Phải chọn ít nhất 1 role'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    const payload = {
      username: form.username.trim(),
      email:    form.email.trim(),
      fullName: form.fullName.trim() || null,
      roles:    form.roles,
      enabled:  form.enabled,
    }
    if (form.password.trim()) {
      payload.password = form.password.trim()
    }
    onSave(payload)
  }

  const allRoles = [...new Set([...COMMON_ROLES, ...form.roles])]

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 32, height: 32, borderRadius: 1.5,
              background: 'linear-gradient(135deg, #0284c7 0%, #3b5bdb 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <PeopleIcon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Typography variant="h6">
              {isEdit ? `Sửa user: ${initialData.username}` : 'Thêm User'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              {isEdit ? 'Cập nhật thông tin và roles của user' : 'Tạo tài khoản mới trong hệ thống'}
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={2.5}>

          {/* Username */}
          <Grid size={6}>
            <TextField
              label="Username *"
              value={form.username}
              onChange={set('username')}
              error={Boolean(errors.username)}
              helperText={errors.username}
              fullWidth
              size="small"
              disabled={isEdit}
              placeholder="alice"
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } } }}
            />
          </Grid>

          {/* Full Name */}
          <Grid size={6}>
            <TextField
              label="Họ và tên"
              value={form.fullName}
              onChange={set('fullName')}
              fullWidth
              size="small"
              placeholder="Nguyễn Văn A"
            />
          </Grid>

          {/* Email */}
          <Grid size={12}>
            <TextField
              label="Email *"
              type="email"
              value={form.email}
              onChange={set('email')}
              error={Boolean(errors.email)}
              helperText={errors.email}
              fullWidth
              size="small"
              placeholder="alice@example.com"
            />
          </Grid>

          {/* Password */}
          <Grid size={12}>
            <TextField
              label={isEdit ? 'Mật khẩu mới (để trống nếu không đổi)' : 'Mật khẩu *'}
              type="password"
              value={form.password}
              onChange={set('password')}
              error={Boolean(errors.password)}
              helperText={errors.password || (isEdit ? 'Bỏ trống để giữ nguyên mật khẩu hiện tại' : '')}
              fullWidth
              size="small"
            />
          </Grid>

          {/* Roles */}
          <Grid size={12}>
            <Typography variant="subtitle2" sx={{ mb: 1, fontSize: '0.82rem' }}>
              Roles *
            </Typography>
            <FormGroup row sx={{ gap: 0.5 }}>
              {allRoles.map(role => (
                <FormControlLabel
                  key={role}
                  control={
                    <Checkbox
                      checked={form.roles.includes(role)}
                      onChange={() => toggleRole(role)}
                      size="small"
                    />
                  }
                  label={
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.78rem' }}
                    >
                      {role}
                    </Typography>
                  }
                  sx={{ mr: 2 }}
                />
              ))}
            </FormGroup>

            {/* Custom role input */}
            <Box sx={{ display: 'flex', gap: 1, mt: 1, alignItems: 'center' }}>
              <TextField
                placeholder="Thêm role mới (VD: MANAGER)"
                value={customRole}
                onChange={e => setCustomRole(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && addCustomRole()}
                size="small"
                sx={{ flex: 1 }}
                slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.78rem' } } }}
              />
              <Button size="small" variant="outlined" onClick={addCustomRole} sx={{ whiteSpace: 'nowrap' }}>
                Thêm
              </Button>
            </Box>

            {form.roles.length > 0 && (
              <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 1 }}>
                {form.roles.map(r => (
                  <Chip
                    key={r}
                    label={r}
                    size="small"
                    onDelete={() => toggleRole(r)}
                    sx={{
                      height: 22,
                      fontSize: '0.65rem',
                      fontFamily: "'JetBrains Mono', monospace",
                      backgroundColor: r.includes('ADMIN') ? '#f3e8ff' : '#eef2ff',
                      color: r.includes('ADMIN') ? '#7c3aed' : '#3b5bdb',
                    }}
                  />
                ))}
              </Box>
            )}
            {errors.roles && (
              <Typography variant="caption" color="error">{errors.roles}</Typography>
            )}
          </Grid>

          {/* Enabled toggle */}
          <Grid size={12}>
            <FormControlLabel
              control={
                <Switch
                  checked={form.enabled}
                  onChange={set('enabled')}
                  color="primary"
                />
              }
              label={
                <Box>
                  <Typography variant="body2" fontWeight={500}>
                    {form.enabled ? 'Tài khoản đang hoạt động' : 'Tài khoản bị vô hiệu hóa'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {form.enabled
                      ? 'User có thể đăng nhập và sử dụng hệ thống'
                      : 'User sẽ không thể đăng nhập'}
                  </Typography>
                </Box>
              }
            />
          </Grid>

          {Object.keys(errors).length > 0 && (
            <Grid size={12}>
              <Alert severity="error" sx={{ borderRadius: 2 }}>
                Vui lòng kiểm tra lại các trường bị lỗi.
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
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo User'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
