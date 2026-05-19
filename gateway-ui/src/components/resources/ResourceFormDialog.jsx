import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Typography, Box, Divider,
  CircularProgress, Alert,
} from '@mui/material'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

export default function ResourceFormDialog({
  open, onClose, onSave, initialData, saving, mode = 'resource',
}) {
  const isEdit = Boolean(initialData?.id)
  const isAction = mode === 'action'

  const [name, setName]     = useState('')
  const [error, setError]   = useState('')

  useEffect(() => {
    if (open) {
      setName(initialData?.name ?? '')
      setError('')
    }
  }, [open, initialData])

  const validate = () => {
    if (!name.trim()) { setError('Tên là bắt buộc'); return false }
    if (!isAction && !/^[a-z0-9_-]+$/.test(name.trim())) {
      setError('Resource name chỉ được dùng chữ thường, số, _ và -')
      return false
    }
    return true
  }

  const handleSave = () => {
    if (!validate()) return
    onSave(name.trim())
  }

  const Icon    = isAction ? BoltIcon : CategoryIcon
  const gradient = isAction
    ? 'linear-gradient(135deg, #d97706 0%, #f59e0b 100%)'
    : 'linear-gradient(135deg, #0284c7 0%, #3b5bdb 100%)'
  const label   = isAction ? 'Action' : 'Resource'
  const hint    = isAction
    ? 'Tên action viết HOA, VD: READ, CREATE, UPDATE, DELETE'
    : 'Tên resource viết thường, VD: products, orders, users'
  const placeholder = isAction ? 'READ' : 'products'

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 32, height: 32, borderRadius: 1.5,
              background: gradient,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Icon sx={{ color: '#fff', fontSize: 17 }} />
          </Box>
          <Box>
            <Typography variant="h6">
              {isEdit ? `Sửa ${label}` : `Thêm ${label}`}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              {hint}
            </Typography>
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <TextField
          label={`${label} name *`}
          value={name}
          onChange={e => { setName(e.target.value); setError('') }}
          onKeyDown={e => e.key === 'Enter' && handleSave()}
          error={Boolean(error)}
          helperText={error || hint}
          fullWidth
          autoFocus
          size="small"
          placeholder={placeholder}
          slotProps={{
            htmlInput: {
              style: {
                fontFamily: "'JetBrains Mono', monospace",
                textTransform: isAction ? 'uppercase' : 'none',
              },
            },
          }}
        />
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
          {saving ? 'Đang lưu...' : isEdit ? `Cập nhật` : `Tạo ${label}`}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
