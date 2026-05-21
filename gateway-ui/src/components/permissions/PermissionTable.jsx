import { useState, useMemo } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, IconButton,
  Tooltip, Typography, Chip, Skeleton,
  TextField, InputAdornment, MenuItem, Select,
  FormControl, InputLabel,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import ShieldIcon from '@mui/icons-material/ShieldRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

function ActionChip({ action }) {
  const cfg = ACTION_COLORS[action] ?? { bg: '#f1f5f9', color: '#475569' }
  return (
    <Chip label={action} size="small" sx={{
      backgroundColor: cfg.bg, color: cfg.color,
      fontSize: '0.65rem', height: 20, fontWeight: 700,
      '& .MuiChip-label': { px: '8px' },
    }} />
  )
}

function SkeletonRows({ count = 6 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[120, 140, 120, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function PermissionTable({ permissions, loading, onEdit, onDelete }) {
  const [search, setSearch]             = useState('')
  const [filterAction, setFilterAction] = useState('all')

  const actions  = useMemo(() => [...new Set(permissions.map(p => p.action))].sort(), [permissions])

  const filtered = useMemo(() => permissions.filter(p => {
    const q = search.toLowerCase()
    const matchSearch =
      !q ||
      p.resource.toLowerCase().includes(q) ||
      p.action.toLowerCase().includes(q) ||
      (p.code ?? '').toLowerCase().includes(q)
    const matchAction = filterAction === 'all' || p.action === filterAction
    return matchSearch && matchAction
  }), [permissions, search, filterAction])

  return (
    <Box>
      {/* ── Toolbar ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField
          placeholder="Tìm theo resource, action, code..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          size="small"
          sx={{ flex: 1, minWidth: 220 }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon sx={{ fontSize: 16, color: '#94a3b8' }} />
                </InputAdornment>
              ),
            },
          }}
        />

        <FormControl size="small" sx={{ minWidth: 130 }}>
          <InputLabel>Action</InputLabel>
          <Select
            value={filterAction}
            label="Action"
            onChange={e => setFilterAction(e.target.value)}
          >
            <MenuItem value="all">Tất cả</MenuItem>
            {actions.map(a => <MenuItem key={a} value={a}>{a}</MenuItem>)}
          </Select>
        </FormControl>

        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
          {filtered.length} / {permissions.length} permissions
        </Typography>
      </Box>

      {/* ── Table ── */}
      <TableContainer component={Paper} elevation={0}
        sx={{ border: '1px solid #e2e8f0', borderRadius: 3, overflow: 'hidden' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Resource</TableCell>
              <TableCell>Action</TableCell>
              <TableCell>Permission Code</TableCell>
              <TableCell align="right">Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={6} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                  <ShieldIcon sx={{ fontSize: 36, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy permission nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((perm) => (
                <TableRow key={perm.id}>
                  <TableCell>
                    <Typography variant="body2" sx={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: '0.82rem', fontWeight: 600, color: '#0f172a',
                    }}>
                      {perm.resource}
                    </Typography>
                  </TableCell>

                  <TableCell>
                    <ActionChip action={perm.action} />
                  </TableCell>

                  <TableCell>
                    <Typography variant="body2" sx={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: '0.75rem', color: '#475569',
                      backgroundColor: '#f8fafc', border: '1px solid #e2e8f0',
                      borderRadius: 1, px: 1, py: 0.25, display: 'inline-block',
                    }}>
                      {perm.code}
                    </Typography>
                  </TableCell>

                  <TableCell align="right">
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                      <Tooltip title="Chỉnh sửa" arrow>
                        <IconButton size="small" onClick={() => onEdit(perm)}
                          sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}>
                          <EditIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Xóa permission" arrow>
                        <IconButton size="small" onClick={() => onDelete(perm)}
                          sx={{ color: '#dc2626', '&:hover': { backgroundColor: '#fee2e2' } }}>
                          <DeleteIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
