import { useState } from 'react'
import {
  Box, Button, Typography, Alert, Snackbar,
  CircularProgress, Paper, Tabs, Tab, Divider,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

import ResourceTable from '../components/resources/ResourceTable'
import ActionTable from '../components/resources/ActionTable'
import ResourceFormDialog from '../components/resources/ResourceFormDialog'
import ResourceDeleteDialog from '../components/resources/ResourceDeleteDialog'
import { useResources } from '../hooks/useResources'

export default function ResourcesPage() {
  const {
    resources, actions,
    loading, saving, error,
    load,
    createResource, updateResource, deleteResource,
    createAction,  updateAction,  deleteAction,
  } = useResources()

  const [tab, setTab]               = useState(0)               // 0 = Resources, 1 = Actions
  const [formOpen, setFormOpen]     = useState(false)
  const [editTarget, setEditTarget] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)

  const [snack, setSnack] = useState({ open: false, message: '', severity: 'success' })
  const toast = (message, severity = 'success') => setSnack({ open: true, message, severity })

  const mode = tab === 0 ? 'resource' : 'action'

  const handleOpenCreate = () => { setEditTarget(null); setFormOpen(true) }
  const handleOpenEdit   = (item) => { setEditTarget(item); setFormOpen(true) }
  const handleCloseForm  = () => { setFormOpen(false); setEditTarget(null) }

  const handleSaveForm = async (name) => {
    try {
      if (mode === 'resource') {
        if (editTarget) { await updateResource(editTarget.id, name); toast('Đã cập nhật resource') }
        else            { await createResource(name); toast('Đã tạo resource') }
      } else {
        if (editTarget) { await updateAction(editTarget.id, name); toast('Đã cập nhật action') }
        else            { await createAction(name); toast('Đã tạo action') }
      }
      handleCloseForm()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const handleConfirmDelete = async (id) => {
    try {
      if (mode === 'resource') { await deleteResource(id); toast('Đã xóa resource') }
      else                     { await deleteAction(id);   toast('Đã xóa action') }
      setDeleteTarget(null)
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const totalPermissions = resources.reduce((s, r) => s + (r.permissionCount ?? 0), 0)

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>

      {/* ── Header ── */}
      <Box sx={{ px: 4, pt: 3.5, pb: 0, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Box
                sx={{
                  width: 36, height: 36, borderRadius: 2,
                  background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}
              >
                <CategoryIcon sx={{ color: '#fff', fontSize: 20 }} />
              </Box>
              <Typography variant="h5">Resource Management</Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Quản lý Resources và Actions — các thành phần tạo nên permission code <code>resource:ACTION</code>.
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
              Thêm {tab === 0 ? 'Resource' : 'Action'}
            </Button>
          </Box>
        </Box>

        {/* Stats */}
        <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
          {[
            { label: 'Resources', value: resources.length, color: '#0284c7', bg: '#e0f2fe', icon: <CategoryIcon sx={{ fontSize: 14 }} /> },
            { label: 'Actions',   value: actions.length,   color: '#d97706', bg: '#fef3c7', icon: <BoltIcon sx={{ fontSize: 14 }} /> },
            { label: 'Permissions tổng', value: totalPermissions, color: '#7c3aed', bg: '#f3e8ff', icon: null },
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
              {icon && <Box sx={{ color, display: 'flex' }}>{icon}</Box>}
              <Typography variant="h6" sx={{ color, fontWeight: 700, lineHeight: 1 }}>{value}</Typography>
              <Typography variant="caption" sx={{ color, opacity: 0.75 }}>{label}</Typography>
            </Paper>
          ))}
        </Box>

        {/* Tabs */}
        <Tabs
          value={tab}
          onChange={(_, v) => { setTab(v); setEditTarget(null) }}
          sx={{ mt: 0 }}
        >
          <Tab
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <CategoryIcon sx={{ fontSize: 16 }} /> Resources
              </Box>
            }
          />
          <Tab
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <BoltIcon sx={{ fontSize: 16 }} /> Actions
              </Box>
            }
          />
        </Tabs>
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

        {tab === 0 ? (
          <ResourceTable
            resources={resources}
            loading={loading}
            onEdit={handleOpenEdit}
            onDelete={setDeleteTarget}
          />
        ) : (
          <ActionTable
            actions={actions}
            loading={loading}
            onEdit={handleOpenEdit}
            onDelete={setDeleteTarget}
          />
        )}
      </Box>

      {/* ── Dialogs ── */}
      <ResourceFormDialog
        open={formOpen}
        onClose={handleCloseForm}
        onSave={handleSaveForm}
        initialData={editTarget}
        saving={saving}
        mode={mode}
      />

      <ResourceDeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        target={deleteTarget}
        saving={saving}
        mode={mode}
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
