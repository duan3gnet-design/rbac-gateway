import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress, Divider,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/WarningAmberRounded'
import ShieldIcon from '@mui/icons-material/ShieldRounded'

export default function PermissionDeleteDialog({ open, onClose, onConfirm, permission, saving }) {
  if (!permission) return null

  const code = permission.code ?? `${permission.resource}:${permission.action}`

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
          <Typography variant="h6">Xóa Permission</Typography>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Bạn sắp xóa permission sau:
        </Typography>

        <Box
          sx={{
            display: 'flex', alignItems: 'center', gap: 1.5,
            backgroundColor: '#faf5ff',
            border: '1px solid #e9d5ff',
            borderRadius: 2, px: 2, py: 1.5, mt: 1, mb: 2,
          }}
        >
          <ShieldIcon sx={{ fontSize: 18, color: '#7c3aed' }} />
          <Box>
            <Typography
              variant="body2"
              sx={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, color: '#7c3aed' }}
            >
              {code}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {permission.role} · {permission.resource} · {permission.action}
            </Typography>
          </Box>
        </Box>

        <Typography variant="body2" color="text.secondary">
          Các route đang dùng permission này sẽ mất liên kết.{' '}
          <strong>Thao tác này không thể hoàn tác.</strong>
        </Typography>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button
          variant="contained"
          color="error"
          onClick={() => onConfirm(permission.id)}
          disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang xóa...' : 'Xóa Permission'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
