import { useState } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, IconButton, Tooltip,
  Typography, Chip, Skeleton, TextField, InputAdornment,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

function ActionChip({ name }) {
  const cfg = ACTION_COLORS[name] ?? { bg: '#f1f5f9', color: '#475569' }
  return (
    <Chip
      label={name}
      size="small"
      sx={{
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontSize: '0.63rem',
        height: 20,
        fontWeight: 700,
        '& .MuiChip-label': { px: '7px' },
      }}
    />
  )
}

function SkeletonRows({ count = 5 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[100, 250, 100, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function ResourceTable({ resources, loading, onEdit, onDelete }) {
  const [search, setSearch] = useState('')

  const filtered = resources.filter(r => {
    const q = search.toLowerCase()
    return !q || r.name.toLowerCase().includes(q)
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <TextField
          placeholder="Tìm resource..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          size="small"
          sx={{ flex: 1, maxWidth: 320 }}
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
        <Typography variant="body2" color="text.secondary">
          {filtered.length} / {resources.length} resources
        </Typography>
      </Box>

      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid #e2e8f0', borderRadius: 3, overflow: 'hidden' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 80 }}>ID</TableCell>
              <TableCell>Resource Name</TableCell>
              <TableCell>Actions đang dùng</TableCell>
              <TableCell align="center" sx={{ width: 100 }}>Permissions</TableCell>
              <TableCell align="right" sx={{ width: 100 }}>Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={5} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <CategoryIcon sx={{ fontSize: 36, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy resource nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((resource) => (
                <TableRow key={resource.id}>
                  <TableCell>
                    <Typography variant="caption" color="text.disabled" sx={{ fontFamily: 'monospace' }}>
                      #{resource.id}
                    </Typography>
                  </TableCell>

                  <TableCell>
                    <Typography
                      variant="body2"
                      sx={{
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 700,
                        color: '#0f172a',
                        fontSize: '0.85rem',
                      }}
                    >
                      {resource.name}
                    </Typography>
                  </TableCell>

                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {(resource.actions ?? []).length === 0 ? (
                        <Typography variant="caption" color="text.disabled" sx={{ fontStyle: 'italic' }}>
                          chưa có permission
                        </Typography>
                      ) : (
                        (resource.actions ?? []).map(a => (
                          <ActionChip key={a.id} name={a.name} />
                        ))
                      )}
                    </Box>
                  </TableCell>

                  <TableCell align="center">
                    <Chip
                      label={resource.permissionCount}
                      size="small"
                      sx={{
                        backgroundColor: resource.permissionCount > 0 ? '#f0fdf4' : '#f8fafc',
                        color: resource.permissionCount > 0 ? '#16a34a' : '#94a3b8',
                        fontWeight: 700,
                        fontSize: '0.75rem',
                        height: 22,
                      }}
                    />
                  </TableCell>

                  <TableCell align="right">
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                      <Tooltip title="Đổi tên" arrow>
                        <IconButton
                          size="small"
                          onClick={() => onEdit(resource)}
                          sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}
                        >
                          <EditIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          resource.permissionCount > 0
                            ? `Không thể xóa — đang dùng bởi ${resource.permissionCount} permission`
                            : 'Xóa resource'
                        }
                        arrow
                      >
                        <span>
                          <IconButton
                            size="small"
                            onClick={() => onDelete(resource)}
                            disabled={resource.permissionCount > 0}
                            sx={{ color: '#dc2626', '&:hover': { backgroundColor: '#fee2e2' } }}
                          >
                            <DeleteIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </span>
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
