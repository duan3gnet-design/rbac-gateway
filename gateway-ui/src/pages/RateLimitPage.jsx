import { useState } from 'react'
import {
  Box, Button, Typography, Alert, Snackbar,
  CircularProgress, Paper,
} from '@mui/material'
import AddIcon from '@mui/icons-material/AddRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import SpeedIcon from '@mui/icons-material/SpeedRounded'
import PersonIcon from '@mui/icons-material/PersonRounded'
import StarIcon from '@mui/icons-material/StarRounded'

import RateLimitTable from '../components/ratelimit/RateLimitTable'
import RateLimitFormDialog from '../components/ratelimit/RateLimitFormDialog'
import RateLimitDeleteDialog from '../components/ratelimit/RateLimitDeleteDialog'
import { useRateLimits } from '../hooks/useRateLimits'

export default function RateLimitPage() {
  const {
    configs, loading, saving, error,
    loadConfigs, createConfig, updateConfig, deleteConfig, toggleConfig,
  } = useRateLimits()

  const [formOpen, setFormOpen]         = useState(false)
  const [editTarget, setEditTarget]     = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)

  const [snack, setSnack] = useState({ open: false, message: '', severity: 'success' })
  const toast = (message, severity = 'success') =>
    setSnack({ open: true, message, severity })

  // ── Handlers ──────────────────────────────────────────────────────────────
  const handleOpenCreate = () => { setEditTarget(null); setFormOpen(true) }
  const handleOpenEdit   = (cfg) => { setEditTarget(cfg); setFormOpen(true) }
  const handleCloseForm  = () => { setFormOpen(false); setEditTarget(null) }

  const handleSaveForm = async (payload) => {
    try {
      if (editTarget) {
        await updateConfig(editTarget.id, payload)
        toast(editTarget.username === null
          ? 'Đã cập nhật Global Default'
          : `Đã cập nhật override cho ${editTarget.username}`)
      } else {
        await createConfig(payload)
        toast(payload.username
          ? `Đã tạo override cho ${payload.username}`
          : 'Đã cập nhật Global Default')
      }
      handleCloseForm()
    } catch (e) {
      toast(e.message || 'Lỗi khi lưu cấu hình', 'error')
    }
  }

  const handleConfirmDelete = async (id) => {
    try {
      await deleteConfig(id)
      setDeleteTarget(null)
      toast('Đã xóa override')
    } catch (e) {
      toast(e.message || 'Lỗi khi xóa', 'error')
    }
  }

  const handleToggle = async (id, enabled) => {
    try {
      await toggleConfig(id, enabled)
      toast(`Override ${enabled ? 'đã bật' : 'đã tắt'}`)
    } catch (e) {
      toast(e.message || 'Lỗi khi thay đổi trạng thái', 'error')
    }
  }

  // ── Stats ─────────────────────────────────────────────────────────────────
  const globalDefault = configs.find(c => c.username === null)
  const overrides     = configs.filter(c => c.username !== null)
  const activeCount   = overrides.filter(c => c.enabled).length

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>

      {/* ── Page Header ─────────────────────────────────────────── */}
      <Box sx={{ px: 4, pt: 3.5, pb: 2.5, backgroundColor: '#fff', borderBottom: '1px solid #e2e8f0' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Box
                sx={{
                  width: 36, height: 36, borderRadius: 2,
                  background: 'linear-gradient(135deg, #d97706 0%, #f59e0b 100%)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}
              >
                <SpeedIcon sx={{ color: '#fff', fontSize: 20 }} />
              </Box>
              <Typography variant="h5">Rate Limit</Typography>
            </Box>
            <Typography variant="body2" color="text.secondary">
              Quản lý giới hạn tốc độ request — Global Default áp dụng cho tất cả, Per-User override ghi đè cho từng tài khoản.
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button
              variant="outlined"
              size="small"
              onClick={loadConfigs}
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
              Thêm Override
            </Button>
          </Box>
        </Box>

        {/* ── Stats bar ── */}
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {[
            {
              label: 'Global Default',
              value: globalDefault
                ? `${globalDefault.replenishRate}r/s · ${globalDefault.burstCapacity}b`
                : '—',
              color: '#a16207', bg: '#fef9c3',
              icon: <StarIcon sx={{ fontSize: 14 }} />,
            },
            {
              label: 'Per-User Override',
              value: overrides.length,
              color: '#0284c7', bg: '#e0f2fe',
              icon: <PersonIcon sx={{ fontSize: 14 }} />,
            },
            {
              label: 'Override đang bật',
              value: activeCount,
              color: '#16a34a', bg: '#dcfce7',
              icon: <SpeedIcon sx={{ fontSize: 14 }} />,
            },
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
              <Typography variant="h6" sx={{ color, fontWeight: 700, lineHeight: 1, fontSize: '1rem' }}>
                {value}
              </Typography>
              <Typography variant="caption" sx={{ color, opacity: 0.75 }}>
                {label}
              </Typography>
            </Paper>
          ))}
        </Box>
      </Box>

      {/* ── Content ─────────────────────────────────────────────── */}
      <Box sx={{ flex: 1, px: 4, py: 3, overflow: 'auto' }}>
        {error && (
          <Alert
            severity="error"
            sx={{ mb: 2, borderRadius: 2 }}
            action={
              <Button size="small" color="inherit" onClick={loadConfigs}>
                Thử lại
              </Button>
            }
          >
            {error}
          </Alert>
        )}

        {/* Callout giải thích cách hoạt động */}
        <Alert
          severity="info"
          icon={<SpeedIcon sx={{ fontSize: 18 }} />}
          sx={{ mb: 2.5, borderRadius: 2, fontSize: '0.82rem' }}
        >
          <strong>Cách hoạt động:</strong> Mỗi request sẽ tra cứu config theo thứ tự:
          Per-User override → Global Default → application.yml fallback.
          Cache Redis TTL 5 phút — thay đổi sẽ có hiệu lực sau tối đa 5 phút.
        </Alert>

        <RateLimitTable
          configs={configs}
          loading={loading}
          onEdit={handleOpenEdit}
          onDelete={setDeleteTarget}
          onToggle={handleToggle}
        />
      </Box>

      {/* ── Dialogs ─────────────────────────────────────────────── */}
      <RateLimitFormDialog
        open={formOpen}
        onClose={handleCloseForm}
        onSave={handleSaveForm}
        initialData={editTarget}
        saving={saving}
      />

      <RateLimitDeleteDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
        config={deleteTarget}
        saving={saving}
      />

      {/* ── Snackbar ─────────────────────────────────────────────── */}
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
