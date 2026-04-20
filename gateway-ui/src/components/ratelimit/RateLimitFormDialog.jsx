import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography, Box, Divider,
  Alert, CircularProgress, Slider, InputAdornment,
  FormControlLabel, Switch, Tooltip,
} from '@mui/material'
import SpeedIcon from '@mui/icons-material/SpeedRounded'
import PersonIcon from '@mui/icons-material/PersonRounded'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'

const DEFAULT_FORM = {
  username: '',
  replenishRate: 20,
  burstCapacity: 40,
  enabled: true,
  description: '',
}

function SliderField({ label, hint, value, onChange, min = 1, max = 500, step = 1, color = '#3b5bdb' }) {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Typography variant="subtitle2" sx={{ fontSize: '0.82rem' }}>{label}</Typography>
          <Tooltip title={hint} arrow>
            <InfoOutlinedIcon sx={{ fontSize: 14, color: '#94a3b8', cursor: 'help' }} />
          </Tooltip>
        </Box>
        <Typography
          variant="body2"
          sx={{
            fontFamily: "'JetBrains Mono', monospace",
            fontWeight: 700,
            color,
            fontSize: '0.9rem',
          }}
        >
          {value}
        </Typography>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Slider
          value={value}
          onChange={(_, v) => onChange(v)}
          min={min}
          max={max}
          step={step}
          sx={{
            flex: 1,
            color,
            '& .MuiSlider-thumb': { width: 16, height: 16 },
            '& .MuiSlider-rail': { backgroundColor: '#e2e8f0' },
          }}
        />
        <TextField
          type="number"
          value={value}
          onChange={e => {
            const v = Math.max(min, Math.min(max, Number(e.target.value)))
            onChange(v)
          }}
          size="small"
          sx={{ width: 80 }}
          slotProps={{ htmlInput: { min, max, step, style: { fontFamily: "'JetBrains Mono', monospace", textAlign: 'center' } } }}
        />
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
        <Typography variant="caption" color="text.disabled">{min}</Typography>
        <Typography variant="caption" color="text.disabled">{max}</Typography>
      </Box>
    </Box>
  )
}

export default function RateLimitFormDialog({ open, onClose, onSave, initialData, saving }) {
  const isEdit     = Boolean(initialData?.id)
  const isGlobal   = isEdit && initialData?.username === null

  const [form, setForm]     = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})

  useEffect(() => {
    if (open) {
      if (initialData) {
        setForm({
          username:      initialData.username ?? '',
          replenishRate: initialData.replenishRate,
          burstCapacity: initialData.burstCapacity,
          enabled:       initialData.enabled,
          description:   initialData.description ?? '',
        })
      } else {
        setForm(DEFAULT_FORM)
      }
      setErrors({})
    }
  }, [open, initialData])

  const set = (field) => (val) => {
    const value = val?.target !== undefined
      ? (val.target.type === 'checkbox' ? val.target.checked : val.target.value)
      : val
    setForm(f => ({ ...f, [field]: value }))
    if (errors[field]) setErrors(e => ({ ...e, [field]: '' }))
  }

  const validate = () => {
    const e = {}
    // Username bắt buộc khi tạo per-user override
    if (!isEdit && !isGlobal) {
      if (!form.username.trim()) e.username = 'Username là bắt buộc'
    }
    if (form.replenishRate < 1) e.replenishRate = 'Phải >= 1'
    if (form.burstCapacity < form.replenishRate) {
      e.burstCapacity = `Burst capacity phải >= replenish rate (${form.replenishRate})`
    }
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    const payload = {
      replenishRate: form.replenishRate,
      burstCapacity: form.burstCapacity,
      enabled:       form.enabled,
      description:   form.description || null,
    }
    if (!isEdit) {
      payload.username = form.username.trim() || null
    }
    onSave(payload)
  }

  // Preview: tính throughput thực
  const reqPerMin = form.replenishRate * 60
  const burstNote = form.burstCapacity > form.replenishRate
    ? `burst ${form.burstCapacity} req ngay lập tức`
    : 'không có burst'

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 32, height: 32, borderRadius: 1.5,
              background: isGlobal
                ? 'linear-gradient(135deg, #d97706 0%, #f59e0b 100%)'
                : 'linear-gradient(135deg, #3b5bdb 0%, #7c3aed 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {isGlobal
              ? <SpeedIcon sx={{ color: '#fff', fontSize: 17 }} />
              : <PersonIcon sx={{ color: '#fff', fontSize: 17 }} />}
          </Box>
          <Box>
            <Typography variant="h6">
              {isGlobal
                ? 'Sửa Global Default'
                : isEdit
                  ? `Sửa override: ${initialData.username}`
                  : 'Thêm Per-User Override'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              {isGlobal
                ? 'Áp dụng cho tất cả user không có override riêng'
                : 'Cấu hình rate limit riêng cho một user cụ thể'}
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={3}>

          {/* Username — chỉ hiện khi tạo mới non-global */}
          {!isEdit && (
            <Grid size={12}>
              <TextField
                label="Username"
                value={form.username}
                onChange={set('username')}
                error={Boolean(errors.username)}
                helperText={errors.username || 'Username của user cần override (để trống = update global default)'}
                fullWidth
                placeholder="alice@example.com"
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <PersonIcon sx={{ fontSize: 16, color: '#94a3b8' }} />
                      </InputAdornment>
                    ),
                  },
                  htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } },
                }}
              />
            </Grid>
          )}

          {/* Replenish Rate slider */}
          <Grid size={12}>
            <SliderField
              label="Replenish Rate"
              hint="Số token được nạp lại vào bucket mỗi giây. Quyết định tốc độ bền vững (sustained rate)."
              value={form.replenishRate}
              onChange={set('replenishRate')}
              max={500}
              color="#3b5bdb"
            />
            {errors.replenishRate && (
              <Typography variant="caption" color="error">{errors.replenishRate}</Typography>
            )}
          </Grid>

          {/* Burst Capacity slider */}
          <Grid size={12}>
            <SliderField
              label="Burst Capacity"
              hint="Dung lượng tối đa của bucket. Cho phép xử lý burst traffic ngắn hạn vượt quá sustained rate."
              value={form.burstCapacity}
              onChange={set('burstCapacity')}
              min={form.replenishRate}
              max={1000}
              color="#7c3aed"
            />
            {errors.burstCapacity && (
              <Typography variant="caption" color="error">{errors.burstCapacity}</Typography>
            )}
          </Grid>

          {/* Preview */}
          <Grid size={12}>
            <Box
              sx={{
                backgroundColor: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: 2,
                p: 2,
              }}
            >
              <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                Preview
              </Typography>
              <Box sx={{ mt: 1, display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                {[
                  { label: 'Sustained', value: `${form.replenishRate} req/s`, color: '#3b5bdb' },
                  { label: 'Per phút',  value: `${reqPerMin.toLocaleString()} req/min`, color: '#0284c7' },
                  { label: 'Burst',     value: burstNote, color: '#7c3aed' },
                ].map(({ label, value, color }) => (
                  <Box key={label}>
                    <Typography variant="caption" color="text.secondary">{label}</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700, color, fontFamily: "'JetBrains Mono', monospace", fontSize: '0.82rem' }}>
                      {value}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          </Grid>

          {/* Description */}
          <Grid size={12}>
            <TextField
              label="Mô tả (tùy chọn)"
              value={form.description}
              onChange={set('description')}
              fullWidth
              multiline
              minRows={2}
              placeholder="Ví dụ: Override cho tài khoản premium, giới hạn thấp cho trial account..."
              helperText="Ghi chú nội bộ để dễ quản lý"
            />
          </Grid>

          {/* Enabled — chỉ cho per-user override */}
          {isEdit && !isGlobal && (
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
                      {form.enabled ? 'Override đang bật' : 'Override đang tắt'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {form.enabled
                        ? 'Config này sẽ được áp dụng cho user'
                        : 'User sẽ dùng global default thay thế'}
                    </Typography>
                  </Box>
                }
              />
            </Grid>
          )}

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
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo Override'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
