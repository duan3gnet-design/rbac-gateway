import { useState } from 'react'
import {
  Box, Button, Typography, Alert, Snackbar,
  CircularProgress, Paper,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import GroupWorkIcon from '@mui/icons-material/GroupWorkRounded'
import PeopleIcon from '@mui/icons-material/PeopleRounded'
import KeyIcon from '@mui/icons-material/KeyRounded'

import RoleTable from '../components/roles/RoleTable'
import RoleFormDialog from '../components/roles/RoleFormDialog'
import RoleDeleteDialog from '../components/roles/RoleDeleteDialog'
import RoleAssignDialog from '../components/roles/RoleAssignDialog'
import { useRoles } from '../hooks/useRoles'

export default function RolesPage() {
  const {
    roles, permissions,
    loading, saving, error,
    load,
    createRole, updateRole, deleteRole, assignPermissions,
  } = useRoles()

  const [formOpen, setFormOpen]       = useState(false)
  const [editTarget, setEditTarget]   = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [assignTarget, setAssignTarget] = useState(null)

  const [snack, setSnack] = useState({ open: false, message: '', severity: 'success' })
  const toast = (message, severity = 'success') => setSnack({ open: true, message, severity })

  const handleOpenCreate = () => { setEditTarget(null); setFormOpen(true) }
  const handleOpenEdit   = (role) => { setEditTarget(role); setFormOpen(true) }
  const handleCloseForm  = () => { setFormOpen(false); setEditTarget(null) }

  const handleSaveForm = async (payload) => {
    try {
      if (editTarget) {
        await updateRole(editTarget.id, payload)
        toast('Cập nhật role thành công')
      } else {
        await createRole(payload)
        toast('Tạo role thành công')
      }
      handleCloseForm()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const handleConfirmDelete = async (id) => {
    try {
      await deleteRole(id)
      setDeleteTarget(null)
      toast('Đã xóa role')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const handleSaveAssign = async (roleId, permissionIds) => {
    try {
      await assignPermissions(roleId, permissionIds)
      setAssignTarget(null)
      toast('Cập nhật permissions thành công')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  // Stats
  const totalUsers = roles.reduce((s, r) => s + (r.userCount ?? 0), 0)
  const totalPermsAssigned = roles.reduce((s, r) => s + (r.permissions?.length ?? 0), 0)

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>

      {/* ── Header ── */}
      <Box sx={{ px: 4, pt: 3.5, pb: 2.5, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Box sx={{
                width: 36, height: 36, borderRadius: 2,
                background: 'linear-gradient(135deg, #7c3aed 0%, #3b5bdb 100%)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <GroupWorkIcon sx={{ color: '#fff', fontSize: 20 }} />
              </Box>
              <Typography variant="h5">Role Management</Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Quản lý roles — tạo role mới, gán permissions từ{' '}
              <code>role_permissions</code> và kiểm soát quyền truy cập theo nhóm.
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button
              variant="outlined"
              size="small"
              onClick={load}
              disabled={loading}
              startIcon={loading ? <CircularProgress size={14} color="inherit" /> : <RefreshIcon sx={{ fontSize: 16 }} />}
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
              Thêm Role
            </Button>
          </Box>
        </Box>

        {/* Stats */}
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {[
            { label: 'Roles',               value: roles.length,       color: '#7c3aed', bg: '#f3e8ff', icon: <GroupWorkIcon sx={{ fontSize: 14 }} /> },
            { label: 'Users được gán role', value: totalUsers,         color: '#0284c7', bg: '#e0f2fe', icon: <PeopleIcon sx={{ fontSize: 14 }} /> },
            { label: 'Permission mappings', value: totalPermsAssigned, color: '#16a34a', bg: '#dcfce7', icon: <KeyIcon sx={{ fontSize: 14 }} /> },
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
              <Typography variant="h6" sx={{ color, fontWeight: 700, lineHeight: 1 }}>{value}</Typography>
              <Typography variant="caption" sx={{ color, opacity: 0.75 }}>{label}</Typography>
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
            action={<Button size="small" color="inherit" onClick={load}>Thử lại</Button>}
          >
            {error}
          </Alert>
        )}

        <RoleTable
          roles={roles}
          loading={loading}
          onEdit={handleOpenEdit}
          onDelete={setDeleteTarget}
          onAssign={setAssignTarget}
        />
      </Box>

      {/* ── Dialogs ── */}
      <RoleFormDialog
        open={formOpen}
        onClose={handleCloseForm}
        onSave={handleSaveForm}
        initialData={editTarget}
        saving={saving}
      />

      <RoleDeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        role={deleteTarget}
        saving={saving}
      />

      <RoleAssignDialog
        open={Boolean(assignTarget)}
        onClose={() => setAssignTarget(null)}
        onSave={handleSaveAssign}
        role={assignTarget}
        allPermissions={permissions}
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
