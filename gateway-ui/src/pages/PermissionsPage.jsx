import { useState } from 'react'
import {
  Box, Button, Typography, Alert, Snackbar,
  CircularProgress, Paper,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import ShieldIcon from '@mui/icons-material/ShieldRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

import PermissionTable from '../components/permissions/PermissionTable'
import PermissionFormDialog from '../components/permissions/PermissionFormDialog'
import PermissionDeleteDialog from '../components/permissions/PermissionDeleteDialog'
import { usePermissions } from '../hooks/usePermissions'

export default function PermissionsPage() {
  const {
    permissions, resources, actions,
    loading, saving, error,
    loadPermissions,
    createPermission, updatePermission, deletePermission,
  } = usePermissions()

  const [formOpen, setFormOpen]         = useState(false)
  const [editTarget, setEditTarget]     = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)

  const [snack, setSnack] = useState({ open: false, message: '', severity: 'success' })
  const toast = (message, severity = 'success') => setSnack({ open: true, message, severity })

  const handleOpenCreate = () => { setEditTarget(null); setFormOpen(true) }
  const handleOpenEdit   = (perm) => { setEditTarget(perm); setFormOpen(true) }
  const handleCloseForm  = () => { setFormOpen(false); setEditTarget(null) }

  const handleSaveForm = async (payload) => {
    try {
      if (editTarget) {
        await updatePermission(editTarget.id, payload)
        toast('Cập nhật permission thành công')
      } else {
        await createPermission(payload)
        toast('Tạo permission thành công')
      }
      handleCloseForm()
    } catch (e) {
      toast(e.message || 'Lỗi khi lưu permission', 'error')
    }
  }

  const handleConfirmDelete = async (id) => {
    try {
      await deletePermission(id)
      setDeleteTarget(null)
      toast('Đã xóa permission')
    } catch (e) {
      toast(e.message || 'Lỗi khi xóa permission', 'error')
    }
  }

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>

      {/* ── Header ── */}
      <Box sx={{ px: 4, pt: 3.5, pb: 2.5, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Box sx={{
                width: 36, height: 36, borderRadius: 2,
                background: 'linear-gradient(135deg, #7c3aed 0%, #a855f7 100%)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <ShieldIcon sx={{ color: '#fff', fontSize: 20 }} />
              </Box>
              <Typography variant="h5">Permission Management</Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Định nghĩa permissions theo cú pháp <code>resource:ACTION</code> — gán cho role qua trang{' '}
              <strong>Roles</strong>.
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button
              variant="outlined" size="small"
              onClick={loadPermissions} disabled={loading}
              startIcon={loading ? <CircularProgress size={14} color="inherit" /> : <RefreshIcon sx={{ fontSize: 16 }} />}
              sx={{ borderColor: '#e2e8f0', color: '#475569' }}
            >
              Tải lại
            </Button>
            <Button variant="contained" size="small" onClick={handleOpenCreate} startIcon={<AddIcon />}>
              Thêm Permission
            </Button>
          </Box>
        </Box>

        {/* Stats */}
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {[
            { label: 'Permissions', value: permissions.length, color: '#7c3aed', bg: '#f3e8ff', icon: <ShieldIcon sx={{ fontSize: 14 }} /> },
            { label: 'Resources',   value: resources.length,   color: '#0284c7', bg: '#e0f2fe', icon: <CategoryIcon sx={{ fontSize: 14 }} /> },
            { label: 'Actions',     value: actions.length,     color: '#d97706', bg: '#fef3c7', icon: <BoltIcon sx={{ fontSize: 14 }} /> },
          ].map(({ label, value, color, bg, icon }) => (
            <Paper key={label} elevation={0} sx={{
              display: 'flex', alignItems: 'center', gap: 1,
              px: 2, py: 1, borderRadius: 2, border: '1px solid #e2e8f0', backgroundColor: bg,
            }}>
              <Box sx={{ color, display: 'flex' }}>{icon}</Box>
              <Typography variant="h6" sx={{ color, fontWeight: 700, lineHeight: 1 }}>{value}</Typography>
              <Typography variant="caption" sx={{ color, opacity: 0.75 }}>{label}</Typography>
            </Paper>
          ))}
        </Box>
      </Box>

      {/* ── Content ── */}
      <Box sx={{ flex: 1, px: 4, py: 3, overflow: 'auto' }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}
            action={<Button size="small" color="inherit" onClick={loadPermissions}>Thử lại</Button>}>
            {error}
          </Alert>
        )}
        <PermissionTable
          permissions={permissions}
          loading={loading}
          onEdit={handleOpenEdit}
          onDelete={setDeleteTarget}
        />
      </Box>

      {/* ── Dialogs ── */}
      <PermissionFormDialog
        open={formOpen}
        onClose={handleCloseForm}
        onSave={handleSaveForm}
        initialData={editTarget}
        saving={saving}
        resources={resources}
        actions={actions}
      />

      <PermissionDeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        permission={deleteTarget}
        saving={saving}
      />

      <Snackbar
        open={snack.open} autoHideDuration={3500}
        onClose={() => setSnack(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert severity={snack.severity}
          onClose={() => setSnack(s => ({ ...s, open: false }))}
          sx={{ borderRadius: 2, boxShadow: 3 }}>
          {snack.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
