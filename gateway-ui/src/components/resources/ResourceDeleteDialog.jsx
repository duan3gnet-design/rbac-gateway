import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress, Divider, Alert,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/WarningAmberRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

export default function ResourceDeleteDialog({
  open, onClose, onConfirm, target, saving, mode = 'resource',
}) {
  if (!target) return null

  const isAction = mode === 'action'
  const label = isAction ? 'Action' : 'Resource'
  const isBlocked = !isAction && (target.permissionCount ?? 0) > 0

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 36, height: 36, borderRadius: 2,
              backgroundColor: isBlocked ? '#fef3c7' : '#fee2e2',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <WarningIcon sx={{ color: isBlocked ? '#d97706' : '#dc2626', fontSize: 20 }} />
          </Box>
          <Typography variant="h6">Xóa {label}</Typography>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Bạn sắp xóa {label.toLowerCase()} sau:
        </Typography>

        <Box
          sx={{
            display: 'flex', alignItems: 'center', gap: 1.5,
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: 2, px: 2, py: 1.5, mt: 1, mb: 2,
          }}
        >
          {isAction
            ? <BoltIcon sx={{ fontSize: 18, color: '#d97706' }} />
            : <CategoryIcon sx={{ fontSize: 18, color: '#0284c7' }} />}
          <Typography
            variant="body2"
            sx={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, color: '#0f172a' }}
          >
            {target.name}
          </Typography>
        </Box>

        {isBlocked ? (
          <Alert severity="warning" sx={{ borderRadius: 2 }}>
            Resource này đang được dùng bởi <strong>{target.permissionCount} permission</strong>.
            Hãy xóa các permissions đó trước rồi mới có thể xóa resource.
          </Alert>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Thao tác này <strong>không thể hoàn tác</strong>. Các permissions liên quan sẽ mất tham chiếu.
          </Typography>
        )}
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">
          {isBlocked ? 'Đóng' : 'Hủy'}
        </Button>
        {!isBlocked && (
          <Button
            variant="contained"
            color="error"
            onClick={() => onConfirm(target.id)}
            disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
          >
            {saving ? 'Đang xóa...' : `Xóa ${label}`}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
