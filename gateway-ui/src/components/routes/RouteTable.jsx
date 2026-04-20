import { useState, useMemo } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, Switch, IconButton,
  Tooltip, Typography, Chip, Skeleton,
  TextField, InputAdornment, MenuItem, Select,
  FormControl, InputLabel,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import ShieldIcon from '@mui/icons-material/ShieldRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import FilterListIcon from '@mui/icons-material/FilterListRounded'
import DragIndicatorIcon from '@mui/icons-material/DragIndicatorRounded'
import { MethodChipGroup } from '../common/MethodChip'
import StatusChip from '../common/StatusChip'
import { parsePredicates, parseFilters, parseService } from '../../hooks/useRoutes'

const FILTER_NAME_COLORS = {
  CircuitBreaker: { bg: '#fef3c7', color: '#d97706' },
  Retry:          { bg: '#e0f2fe', color: '#0284c7' },
  AddRequestHeader: { bg: '#f3e8ff', color: '#7c3aed' },
  StripPrefix:    { bg: '#f1f5f9', color: '#475569' },
}

function FilterNameChip({ name }) {
  const cfg = FILTER_NAME_COLORS[name] ?? { bg: '#f1f5f9', color: '#475569' }
  return (
    <Chip
      label={name}
      size="small"
      sx={{
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontSize: '0.65rem',
        height: 18,
        fontWeight: 500,
        '& .MuiChip-label': { px: '6px' },
      }}
    />
  )
}

function SkeletonRows({ count = 5 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[40, 140, 200, 100, 100, 140, 80, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function RouteTable({
  routes, loading, onEdit, onDelete, onToggle, onPermission,
}) {
  const [search, setSearch]         = useState('')
  const [filterStatus, setStatus]   = useState('all')
  const [filterService, setService] = useState('all')

  // Danh sách service unique từ routes
  const services = useMemo(() => {
    const set = new Set(routes.map(r => parseService(r.uri)))
    return [...set]
  }, [routes])

  const filtered = useMemo(() => {
    return routes
      .filter(r => {
        const { path } = parsePredicates(r.predicates)
        const matchSearch =
          r.id.toLowerCase().includes(search.toLowerCase()) ||
          path.toLowerCase().includes(search.toLowerCase()) ||
          r.uri.toLowerCase().includes(search.toLowerCase())
        const matchStatus =
          filterStatus === 'all' ||
          (filterStatus === 'active' && r.enabled) ||
          (filterStatus === 'inactive' && !r.enabled)
        const matchService =
          filterService === 'all' || parseService(r.uri) === filterService
        return matchSearch && matchStatus && matchService
      })
      .sort((a, b) => a.routeOrder - b.routeOrder)
  }, [routes, search, filterStatus, filterService])

  return (
    <Box>
      {/* ── Toolbar ──────────────────────────────────────────────── */}
      <Box
        sx={{
          display: 'flex', alignItems: 'center', gap: 2,
          mb: 2, flexWrap: 'wrap',
        }}
      >
        <TextField
          placeholder="Tìm theo ID, path, URI..."
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
          <InputLabel>Trạng thái</InputLabel>
          <Select
            value={filterStatus}
            label="Trạng thái"
            onChange={e => setStatus(e.target.value)}
            startAdornment={<FilterListIcon sx={{ fontSize: 16, color: '#94a3b8', mr: 0.5 }} />}
          >
            <MenuItem value="all">Tất cả</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="inactive">Inactive</MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 170 }}>
          <InputLabel>Service</InputLabel>
          <Select
            value={filterService}
            label="Service"
            onChange={e => setService(e.target.value)}
          >
            <MenuItem value="all">Tất cả</MenuItem>
            {services.map(s => (
              <MenuItem key={s} value={s}>
                <Typography
                  variant="body2"
                  sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.78rem' }}
                >
                  {s}
                </Typography>
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
          {filtered.length} / {routes.length} routes
        </Typography>
      </Box>

      {/* ── Table ────────────────────────────────────────────────── */}
      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid #e2e8f0', borderRadius: 3, overflow: 'hidden' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 32 }} />
              <TableCell>Route ID</TableCell>
              <TableCell>Path</TableCell>
              <TableCell>Method</TableCell>
              <TableCell>Upstream</TableCell>
              <TableCell>Filters</TableCell>
              <TableCell align="center">Status</TableCell>
              <TableCell align="right">Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={6} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy route nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((route) => {
                const { path, methods } = parsePredicates(route.predicates)
                const filterNames        = parseFilters(route.filters)
                const service            = parseService(route.uri)

                return (
                  <TableRow
                    key={route.id}
                    sx={{ opacity: route.enabled ? 1 : 0.55 }}
                  >
                    {/* Drag handle (visual only) */}
                    <TableCell sx={{ pl: 1, pr: 0, color: '#cbd5e1' }}>
                      <DragIndicatorIcon sx={{ fontSize: 16, display: 'block' }} />
                    </TableCell>

                    {/* Route ID */}
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: "'JetBrains Mono', monospace",
                            fontWeight: 600,
                            color: '#0f172a',
                            fontSize: '0.8rem',
                          }}
                        >
                          {route.id}
                        </Typography>
                        {(route.permissionIds?.length > 0) && (
                          <Tooltip title={`${route.permissionIds.length} permission`} arrow>
                            <ShieldIcon sx={{ fontSize: 14, color: '#7c3aed' }} />
                          </Tooltip>
                        )}
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        order: {route.routeOrder}
                      </Typography>
                    </TableCell>

                    {/* Path */}
                    <TableCell>
                      <Tooltip title={path} arrow>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: "'JetBrains Mono', monospace",
                            fontSize: '0.78rem',
                            color: '#334155',
                            maxWidth: 200,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {path}
                        </Typography>
                      </Tooltip>
                    </TableCell>

                    {/* Method */}
                    <TableCell>
                      <MethodChipGroup methods={methods} />
                    </TableCell>

                    {/* Upstream service */}
                    <TableCell>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: "'JetBrains Mono', monospace",
                          fontSize: '0.75rem',
                          color: '#475569',
                          backgroundColor: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          borderRadius: 1,
                          px: 1, py: 0.25,
                          display: 'inline-block',
                        }}
                      >
                        {service}
                      </Typography>
                    </TableCell>

                    {/* Filters */}
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                        {filterNames.length === 0 ? (
                          <Typography variant="caption" color="text.disabled">—</Typography>
                        ) : (
                          filterNames.slice(0, 2).map(n => (
                            <FilterNameChip key={n} name={n} />
                          ))
                        )}
                        {filterNames.length > 2 && (
                          <Tooltip title={filterNames.slice(2).join(', ')} arrow>
                            <Chip
                              label={`+${filterNames.length - 2}`}
                              size="small"
                              sx={{ height: 18, fontSize: '0.65rem', '& .MuiChip-label': { px: '5px' } }}
                            />
                          </Tooltip>
                        )}
                      </Box>
                    </TableCell>

                    {/* Status toggle */}
                    <TableCell align="center">
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}>
                        <StatusChip enabled={route.enabled} />
                        <Switch
                          checked={route.enabled}
                          onChange={e => onToggle(route.id, e.target.checked)}
                          size="small"
                          sx={{ mt: 0.25 }}
                        />
                      </Box>
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="right">
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title="Phân quyền" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onPermission(route)}
                            sx={{
                              color: '#7c3aed',
                              '&:hover': { backgroundColor: '#ede9fe' },
                            }}
                          >
                            <ShieldIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Chỉnh sửa" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onEdit(route)}
                            sx={{
                              color: '#3b5bdb',
                              '&:hover': { backgroundColor: '#eef2ff' },
                            }}
                          >
                            <EditIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Xóa route" arrow>
                          <IconButton
                            size="small"
                            onClick={() => onDelete(route)}
                            sx={{
                              color: '#dc2626',
                              '&:hover': { backgroundColor: '#fee2e2' },
                            }}
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
