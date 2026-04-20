import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress, Divider,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/WarningAmberRounded'
import PersonIcon from '@mui/icons-material/PersonRounded'

export default function RateLimitDeleteDialog({ open, onClose, onConfirm, config, saving }) {
  if (!config) return null

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
          <Typography variant="h6">Xóa Rate Limit Override</Typography>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Bạn sắp xóa cấu hình rate limit override của:
        </Typography>
        <Box
          sx={{
            display: 'flex', alignItems: 'center', gap: 1,
            backgroundColor: '#f1f5f9',
            border: '1px solid #e2e8f0',
            borderRadius: 2, px: 2, py: 1.5, mt: 1, mb: 2,
          }}
        >
          <PersonIcon sx={{ fontSize: 18, color: '#3b5bdb' }} />
          <Typography
            variant="body2"
            sx={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, color: '#0f172a' }}
          >
            {config.username}
          </Typography>
        </Box>
        <Typography variant="body2" color="text.secondary">
          Sau khi xóa, user này sẽ được áp dụng{' '}
          <strong>Global Default</strong> thay thế. Thao tác này không thể hoàn tác.
        </Typography>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
        <Button
          variant="contained"
          color="error"
          onClick={() => onConfirm(config.id)}
          disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang xóa...' : 'Xóa override'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
