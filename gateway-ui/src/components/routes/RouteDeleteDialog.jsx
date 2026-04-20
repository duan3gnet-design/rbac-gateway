import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress,
} from '@mui/material'
import WarningAmberIcon from '@mui/icons-material/WarningAmberRounded'

export default function RouteDeleteDialog({ open, onClose, onConfirm, route, saving }) {
  if (!route) return null
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 38, height: 38, borderRadius: 2,
              backgroundColor: '#fee2e2',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <WarningAmberIcon sx={{ color: '#dc2626', fontSize: 20 }} />
          </Box>
          <Typography variant="h6">Xóa Route</Typography>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Bạn có chắc muốn xóa route này không? Hành động này không thể hoàn tác.
        </Typography>
        <Box
          sx={{
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: 2,
            px: 2, py: 1.5,
          }}
        >
          <Typography
            variant="body2"
            sx={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 600, color: '#0f172a' }}
          >
            {route.id}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {route.uri}
          </Typography>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">
          Hủy
        </Button>
        <Button
          onClick={() => onConfirm(route.id)}
          variant="contained"
          color="error"
          disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang xóa...' : 'Xóa'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
