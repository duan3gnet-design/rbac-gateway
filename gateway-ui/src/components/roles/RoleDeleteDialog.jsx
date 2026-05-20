import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, Typography, Box, CircularProgress, Divider, Alert, Chip,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/WarningAmberRounded'
import GroupWorkIcon from '@mui/icons-material/GroupWorkRounded'

export default function RoleDeleteDialog({ open, onClose, onConfirm, role, saving }) {
  if (!role) return null

  const isBlocked = (role.userCount ?? 0) > 0
  const isAdmin   = role.name.includes('ADMIN')

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 36, height: 36, borderRadius: 2,
            backgroundColor: isBlocked ? '#fef3c7' : '#fee2e2',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <WarningIcon sx={{ color: isBlocked ? '#d97706' : '#dc2626', fontSize: 20 }} />
          </Box>
          <Typography variant="h6">Xóa Role</Typography>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2 }}>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Bạn sắp xóa role sau:
        </Typography>

        <Box sx={{
          display: 'flex', alignItems: 'center', gap: 1.5,
          backgroundColor: '#f8fafc', border: '1px solid #e2e8f0',
          borderRadius: 2, px: 2, py: 1.5, mt: 1, mb: 2,
        }}>
          <GroupWorkIcon sx={{ fontSize: 18, color: isAdmin ? '#7c3aed' : '#475569' }} />
          <Chip
            label={role.name}
            size="small"
            sx={{
              backgroundColor: isAdmin ? '#f3e8ff' : '#f1f5f9',
              color: isAdmin ? '#7c3aed' : '#475569',
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: '0.78rem', fontWeight: 700, height: 24,
              '& .MuiChip-label': { px: '10px' },
            }}
          />
          <Typography variant="caption" color="text.secondary">
            · {role.permissions?.length ?? 0} permissions · {role.userCount} users
          </Typography>
        </Box>

        {isBlocked ? (
          <Alert severity="warning" sx={{ borderRadius: 2 }}>
            Role này đang được gán cho <strong>{role.userCount} user</strong>.
            Hãy bỏ role khỏi các users đó trước khi xóa.
          </Alert>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Tất cả permission mappings trong <code>role_permissions</code> sẽ bị xóa theo.{' '}
            <strong>Thao tác này không thể hoàn tác.</strong>
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
            onClick={() => onConfirm(role.id)}
            disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
          >
            {saving ? 'Đang xóa...' : 'Xóa Role'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
