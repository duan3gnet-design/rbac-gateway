import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress, Divider, Avatar,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/WarningAmberRounded'

export default function UserDeleteDialog({ open, onClose, onConfirm, user, saving }) {
  if (!user) return null

  const hue = ((user.username || user.email || '').split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)) % 360
  const label = (user.username || user.email || '?')[0].toUpperCase()

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 36, height: 36, borderRadius: 2,
              backgroundColor: '#fee2e2',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <WarningIcon sx={{ color: '#dc2626', fontSize: 20 }} />
          </Box>
          <Typography variant="h6">Xóa User</Typography>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Bạn sắp xóa tài khoản sau:
        </Typography>

        <Box
          sx={{
            display: 'flex', alignItems: 'center', gap: 1.5,
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: 2, px: 2, py: 1.5, mt: 1, mb: 2,
          }}
        >
          <Avatar sx={{ width: 32, height: 32, fontSize: '0.8rem', fontWeight: 700, background: `hsl(${hue}, 60%, 50%)` }}>
            {label}
          </Avatar>
          <Box>
            <Typography variant="body2" sx={{ fontWeight: 700, color: '#0f172a' }}>
              {user.username}
            </Typography>
            {user.email && (
              <Typography variant="caption" color="text.secondary">
                {user.email}
              </Typography>
            )}
          </Box>
        </Box>

        <Typography variant="body2" color="text.secondary">
          Tất cả dữ liệu liên quan (sessions, refresh tokens) sẽ bị xóa.{' '}
          <strong>Thao tác này không thể hoàn tác.</strong>
        </Typography>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button
          variant="contained"
          color="error"
          onClick={() => onConfirm(user.id)}
          disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang xóa...' : 'Xóa User'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
