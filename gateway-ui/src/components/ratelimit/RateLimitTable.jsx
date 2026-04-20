import { useMemo, useState } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, Switch, IconButton,
  Tooltip, Typography, Chip, Skeleton, TextField,
  InputAdornment, LinearProgress,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import StarIcon from '@mui/icons-material/StarRounded'
import PersonIcon from '@mui/icons-material/PersonRounded'
import SpeedIcon from '@mui/icons-material/SpeedRounded'

function SkeletonRows({ count = 4 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[120, 180, 110, 110, 200, 80, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

/**
 * Visual indicator cho tốc độ giới hạn:
 *  ≥ 100 req/s  → xanh lá
 *  ≥ 20  req/s  → xanh dương
 *  ≥ 5   req/s  → vàng
 *  < 5   req/s  → đỏ
 */
function RateBar({ value, max = 200 }) {
  const pct = Math.min((value / max) * 100, 100)
  const color =
    value >= 100 ? '#16a34a' :
    value >= 20  ? '#3b5bdb' :
    value >= 5   ? '#d97706' : '#dc2626'

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 140 }}>
      <Box sx={{ flex: 1 }}>
        <LinearProgress
          variant="determinate"
          value={pct}
          sx={{
            height: 6, borderRadius: 3,
            backgroundColor: '#f1f5f9',
            '& .MuiLinearProgress-bar': { backgroundColor: color, borderRadius: 3 },
          }}
        />
      </Box>
      <Typography
        variant="body2"
        sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem', color, fontWeight: 700, minWidth: 32 }}
      >
        {value}
      </Typography>
    </Box>
  )
}

export default function RateLimitTable({ configs, loading, onEdit, onDelete, onToggle }) {
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    if (!search.trim()) return configs
    const q = search.toLowerCase()
    return configs.filter(c =>
      (c.username ?? 'global default').toLowerCase().includes(q) ||
      (c.description ?? '').toLowerCase().includes(q)
    )
  }, [configs, search])

  const globalDefault = configs.find(c => c.username === null)
  const overrides     = configs.filter(c => c.username !== null)

  return (
    <Box>
      {/* ── Toolbar ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <TextField
          placeholder="Tìm theo username, mô tả..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          size="small"
          sx={{ flex: 1, maxWidth: 360 }}
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
        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
          {overrides.length} override • 1 global default
        </Typography>
      </Box>

      {/* ── Table ── */}
      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid #e2e8f0', borderRadius: 3, overflow: 'hidden' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Loại</TableCell>
              <TableCell>User / Scope</TableCell>
              <TableCell>Replenish Rate</TableCell>
              <TableCell>Burst Capacity</TableCell>
              <TableCell>Mô tả</TableCell>
              <TableCell align="center">Trạng thái</TableCell>
              <TableCell align="right">Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={5} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <SpeedIcon sx={{ fontSize: 36, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy cấu hình nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((cfg) => {
                const isGlobal = cfg.username === null
                return (
                  <TableRow
                    key={cfg.id}
                    sx={{
                      opacity: cfg.enabled ? 1 : 0.55,
                      backgroundColor: isGlobal ? '#fafbff' : 'inherit',
                    }}
                  >
                    {/* Loại */}
                    <TableCell>
                      {isGlobal ? (
                        <Chip
                          icon={<StarIcon sx={{ fontSize: '13px !important' }} />}
                          label="Global Default"
                          size="small"
                          sx={{
                            backgroundColor: '#fef9c3',
                            color: '#a16207',
                            fontWeight: 700,
                            fontSize: '0.68rem',
                            height: 22,
                            '& .MuiChip-icon': { color: '#a16207', ml: '6px' },
                            '& .MuiChip-label': { pl: '4px', pr: '8px' },
                          }}
                        />
                      ) : (
                        <Chip
                          icon={<PersonIcon sx={{ fontSize: '13px !important' }} />}
                          label="Per User"
                          size="small"
                          sx={{
                            backgroundColor: '#e0f2fe',
                            color: '#0284c7',
                            fontWeight: 600,
                            fontSize: '0.68rem',
                            height: 22,
                            '& .MuiChip-icon': { color: '#0284c7', ml: '6px' },
                            '& .MuiChip-label': { pl: '4px', pr: '8px' },
                          }}
                        />
                      )}
                    </TableCell>

                    {/* User / Scope */}
                    <TableCell>
                      {isGlobal ? (
                        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                          Tất cả user
                        </Typography>
                      ) : (
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: "'JetBrains Mono', monospace",
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            color: '#0f172a',
                          }}
                        >
                          {cfg.username}
                        </Typography>
                      )}
                    </TableCell>

                    {/* Replenish Rate */}
                    <TableCell>
                      <Tooltip title="Số token/giây được nạp lại vào bucket" arrow>
                        <Box>
                          <RateBar value={cfg.replenishRate} />
                          <Typography variant="caption" color="text.secondary">
                            token/s
                          </Typography>
                        </Box>
                      </Tooltip>
                    </TableCell>

                    {/* Burst Capacity */}
                    <TableCell>
                      <Tooltip title="Dung lượng tối đa của bucket (cho phép burst)" arrow>
                        <Box>
                          <RateBar value={cfg.burstCapacity} max={400} />
                          <Typography variant="caption" color="text.secondary">
                            max tokens
                          </Typography>
                        </Box>
                      </Tooltip>
                    </TableCell>

                    {/* Mô tả */}
                    <TableCell>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      >
                        {cfg.description || <span style={{ fontStyle: 'italic', opacity: 0.5 }}>—</span>}
                      </Typography>
                    </TableCell>

                    {/* Status */}
                    <TableCell align="center">
                      <Switch
                        checked={cfg.enabled}
                        onChange={e => onToggle(cfg.id, e.target.checked)}
                        size="small"
                        disabled={isGlobal} // global default không tắt được
                      />
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="right">
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title="Chỉnh sửa" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onEdit(cfg)}
                            sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}
                          >
                            <EditIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={isGlobal ? 'Không thể xóa global default' : 'Xóa override'} arrow>
                          <span>
                            <IconButton
                              size="small"
                              onClick={() => onDelete(cfg)}
                              disabled={isGlobal}
                              sx={{
                                color: '#dc2626',
                                '&:hover': { backgroundColor: '#fee2e2' },
                                '&.Mui-disabled': { opacity: 0.3 },
                              }}
                            >
                              <DeleteIcon sx={{ fontSize: 17 }} />
                            </IconButton>
                          </span>
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
