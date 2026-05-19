import { useState } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, IconButton, Tooltip,
  Typography, Chip, Skeleton, TextField, InputAdornment,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

function SkeletonRows({ count = 4 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[60, 140, 120, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function ActionTable({ actions, loading, onEdit, onDelete }) {
  const [search, setSearch] = useState('')

  const filtered = actions.filter(a => {
    const q = search.toLowerCase()
    return !q || a.name.toLowerCase().includes(q)
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <TextField
          placeholder="Tìm action..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          size="small"
          sx={{ flex: 1, maxWidth: 280 }}
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
          {filtered.length} / {actions.length} actions
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
              <TableCell>Action Name</TableCell>
              <TableCell>Preview</TableCell>
              <TableCell align="right" sx={{ width: 100 }}>Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={4} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 5 }}>
                  <BoltIcon sx={{ fontSize: 32, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy action nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((action) => {
                const cfg = ACTION_COLORS[action.name] ?? { bg: '#f1f5f9', color: '#475569' }
                return (
                  <TableRow key={action.id}>
                    <TableCell>
                      <Typography variant="caption" color="text.disabled" sx={{ fontFamily: 'monospace' }}>
                        #{action.id}
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
                        {action.name}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Chip
                        label={action.name}
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
                    </TableCell>

                    <TableCell align="right">
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title="Đổi tên" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onEdit(action)}
                            sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}
                          >
                            <EditIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Xóa action" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onDelete(action)}
                            sx={{ color: '#dc2626', '&:hover': { backgroundColor: '#fee2e2' } }}
                          >
                            <DeleteIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
