import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Typography, Box, Divider,
  CircularProgress,
} from '@mui/material'
import GroupWorkIcon from '@mui/icons-material/GroupWorkRounded'

export default function RoleFormDialog({ open, onClose, onSave, initialData, saving }) {
  const isEdit = Boolean(initialData?.id)
  const [name, setName]   = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open) return
    setError('')
    setName(initialData?.name ?? '')
  }, [open, initialData])

  const normalize = (val) => {
    const upper = val.toUpperCase().trim()
    return upper.startsWith('ROLE_') ? upper : upper ? `ROLE_${upper}` : ''
  }

  const handleChange = (e) => {
    setName(e.target.value)
    setError('')
  }

  const validate = () => {
    const normalized = normalize(name)
    if (!normalized) { setError('Tên role là bắt buộc'); return null }
    if (!/^ROLE_[A-Z0-9_]+$/.test(normalized)) {
      setError('Chỉ dùng chữ hoa, số và dấu _ (VD: ROLE_ADMIN)')
      return null
    }
    return normalized
  }

  const handleSave = () => {
    const normalized = validate()
    if (!normalized) return
    // Gửi name đã normalize, permissionIds sẽ được gán riêng qua AssignDialog
    onSave({ name: normalized, permissionIds: initialData?.permissions?.map(p => p.id) ?? [] })
  }

  const preview = normalize(name)

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5, flexShrink: 0,
            background: 'linear-gradient(135deg, #7c3aed 0%, #3b5bdb 100%)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <GroupWorkIcon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Typography variant="h6">{isEdit ? 'Sửa Role' : 'Thêm Role'}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              Tên role theo chuẩn Spring Security (ROLE_XXX)
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <TextField
          label="Role name *"
          value={name}
          onChange={handleChange}
          onKeyDown={e => e.key === 'Enter' && handleSave()}
          error={Boolean(error)}
          helperText={error || 'VD: ADMIN → tự động thành ROLE_ADMIN'}
          fullWidth
          autoFocus
          size="small"
          placeholder="ADMIN"
          slotProps={{
            htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", textTransform: 'uppercase' } },
          }}
        />

        {/* Preview */}
        {preview && !error && (
          <Box sx={{
            mt: 2, display: 'flex', alignItems: 'center', gap: 1.5,
            backgroundColor: '#faf5ff', border: '1px solid #e9d5ff',
            borderRadius: 2, px: 2, py: 1.25,
          }}>
            <Typography variant="caption" color="text.secondary" fontWeight={600}>
              Sẽ lưu là:
            </Typography>
            <Typography sx={{
              fontFamily: "'JetBrains Mono', monospace",
              fontWeight: 700, fontSize: '0.85rem', color: '#7c3aed',
              backgroundColor: '#f3e8ff', border: '1px solid #e9d5ff',
              borderRadius: 1, px: 1, py: 0.25,
            }}>
              {preview}
            </Typography>
          </Box>
        )}
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
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo Role'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
