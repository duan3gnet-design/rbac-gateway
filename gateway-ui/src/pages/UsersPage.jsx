import { useState } from 'react'
import {
  Box, Button, Typography, Alert, Snackbar,
  CircularProgress, Paper,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import PeopleIcon from '@mui/icons-material/PeopleRounded'
import CheckCircleIcon from '@mui/icons-material/CheckCircleRounded'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettingsRounded'

import UserTable from '../components/users/UserTable'
import UserFormDialog from '../components/users/UserFormDialog'
import UserDeleteDialog from '../components/users/UserDeleteDialog'
import { useUsers } from '../hooks/useUsers'

export default function UsersPage() {
  const {
    users, loading, saving, error,
    loadUsers, createUser, updateUser, deleteUser, toggleUser,
  } = useUsers()

  const [formOpen, setFormOpen]         = useState(false)
  const [editTarget, setEditTarget]     = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)

  const [snack, setSnack] = useState({ open: false, message: '', severity: 'success' })
  const toast = (message, severity = 'success') =>
    setSnack({ open: true, message, severity })

  const handleOpenCreate = () => { setEditTarget(null); setFormOpen(true) }
  const handleOpenEdit   = (user) => { setEditTarget(user); setFormOpen(true) }
  const handleCloseForm  = () => { setFormOpen(false); setEditTarget(null) }

  const handleSaveForm = async (payload) => {
    try {
      if (editTarget) {
        await updateUser(editTarget.id, payload)
        toast(`Cập nhật user ${payload.username || editTarget.username} thành công`)
      } else {
        await createUser(payload)
        toast(`Tạo user ${payload.username} thành công`)
      }
      handleCloseForm()
    } catch (e) {
      toast(e.message || 'Lỗi khi lưu user', 'error')
    }
  }

  const handleConfirmDelete = async (id) => {
    try {
      await deleteUser(id)
      setDeleteTarget(null)
      toast('Đã xóa user')
    } catch (e) {
      toast(e.message || 'Lỗi khi xóa user', 'error')
    }
  }

  const handleToggle = async (id, enabled) => {
    try {
      await toggleUser(id, enabled)
      toast(`User ${enabled ? 'đã bật' : 'đã vô hiệu hóa'}`)
    } catch (e) {
      toast(e.message || 'Lỗi khi thay đổi trạng thái', 'error')
    }
  }

  // ── Stats ──────────────────────────────────────────────────────────────────
  const activeCount = users.filter(u => u.enabled).length
  const adminCount  = users.filter(u => (u.roles ?? []).some(r => r.toUpperCase().includes('ADMIN'))).length

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>

      {/* ── Page Header ── */}
      <Box sx={{ px: 4, pt: 3.5, pb: 2.5, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Box
                sx={{
                  width: 36, height: 36, borderRadius: 2,
                  background: 'linear-gradient(135deg, #0284c7 0%, #3b5bdb 100%)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}
              >
                <PeopleIcon sx={{ color: '#fff', fontSize: 20 }} />
              </Box>
              <Typography variant="h5">User Management</Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Quản lý tài khoản người dùng — tạo mới, gán roles và kiểm soát trạng thái truy cập.
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button
              variant="outlined"
              size="small"
              onClick={loadUsers}
              disabled={loading}
              startIcon={
                loading
                  ? <CircularProgress size={14} color="inherit" />
                  : <RefreshIcon sx={{ fontSize: 16 }} />
              }
              sx={{ borderColor: '#e2e8f0', color: '#475569' }}
            >
              Tải lại
            </Button>
            <Button
              variant="contained"
              size="small"
              onClick={handleOpenCreate}
              startIcon={<AddIcon />}
            >
              Thêm User
            </Button>
          </Box>
        </Box>

        {/* Stats bar */}
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {[
            { label: 'Tổng users',  value: users.length,  color: '#3b5bdb', bg: '#eef2ff', icon: <PeopleIcon sx={{ fontSize: 14 }} /> },
            { label: 'Đang hoạt động', value: activeCount, color: '#16a34a', bg: '#dcfce7', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
            { label: 'Admin',       value: adminCount,    color: '#7c3aed', bg: '#f3e8ff', icon: <AdminPanelSettingsIcon sx={{ fontSize: 14 }} /> },
          ].map(({ label, value, color, bg, icon }) => (
            <Paper
              key={label}
              elevation={0}
              sx={{
                display: 'flex', alignItems: 'center', gap: 1,
                px: 2, py: 1, borderRadius: 2,
                border: '1px solid #e2e8f0',
                backgroundColor: bg,
              }}
            >
              <Box sx={{ color, display: 'flex' }}>{icon}</Box>
              <Typography variant="h6" sx={{ color, fontWeight: 700, lineHeight: 1 }}>
                {value}
              </Typography>
              <Typography variant="caption" sx={{ color, opacity: 0.75 }}>
                {label}
              </Typography>
            </Paper>
          ))}
        </Box>
      </Box>

      {/* ── Content ── */}
      <Box sx={{ flex: 1, px: 4, py: 3, overflow: 'auto' }}>
        {error && (
          <Alert
            severity="error"
            sx={{ mb: 2, borderRadius: 2 }}
            action={
              <Button size="small" color="inherit" onClick={loadUsers}>
                Thử lại
              </Button>
            }
          >
            {error}
          </Alert>
        )}

        <UserTable
          users={users}
          loading={loading}
          onEdit={handleOpenEdit}
          onDelete={setDeleteTarget}
          onToggle={handleToggle}
        />
      </Box>

      {/* ── Dialogs ── */}
      <UserFormDialog
        open={formOpen}
        onClose={handleCloseForm}
        onSave={handleSaveForm}
        initialData={editTarget}
        saving={saving}
      />

      <UserDeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        user={deleteTarget}
        saving={saving}
      />

      {/* ── Snackbar ── */}
      <Snackbar
        open={snack.open}
        autoHideDuration={3500}
        onClose={() => setSnack(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert
          severity={snack.severity}
          onClose={() => setSnack(s => ({ ...s, open: false }))}
          sx={{ borderRadius: 2, boxShadow: 3 }}
        >
          {snack.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
