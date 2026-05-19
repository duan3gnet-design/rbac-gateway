import { useState, useMemo } from 'react'
import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, Switch, IconButton,
  Tooltip, Typography, Chip, Skeleton, Avatar,
  TextField, InputAdornment, MenuItem, Select,
  FormControl, InputLabel,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import SearchIcon from '@mui/icons-material/SearchRounded'
import FilterListIcon from '@mui/icons-material/FilterListRounded'
import PeopleIcon from '@mui/icons-material/PeopleRounded'
import StatusChip from '../common/StatusChip'

function RoleChip({ role }) {
  const isAdmin = role?.toUpperCase().includes('ADMIN')
  return (
    <Chip
      label={role}
      size="small"
      sx={{
        backgroundColor: isAdmin ? '#f3e8ff' : '#f1f5f9',
        color: isAdmin ? '#7c3aed' : '#475569',
        fontSize: '0.65rem',
        height: 20,
        fontWeight: 600,
        fontFamily: "'JetBrains Mono', monospace",
        '& .MuiChip-label': { px: '6px' },
      }}
    />
  )
}

function UserAvatar({ username, email }) {
  const label = (username || email || '?')[0].toUpperCase()
  const hue   = ((username || email || '').split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)) % 360
  return (
    <Avatar
      sx={{
        width: 30, height: 30,
        fontSize: '0.75rem',
        fontWeight: 700,
        background: `hsl(${hue}, 60%, 50%)`,
      }}
    >
      {label}
    </Avatar>
  )
}

function SkeletonRows({ count = 5 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[40, 180, 160, 130, 80, 80, 90].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function UserTable({ users, loading, onEdit, onDelete, onToggle }) {
  const [search, setSearch]         = useState('')
  const [filterRole, setFilterRole] = useState('all')
  const [filterStatus, setStatus]   = useState('all')

  const roles = useMemo(() => {
    const set = new Set()
    users.forEach(u => (u.roles ?? []).forEach(r => set.add(r)))
    return [...set].sort()
  }, [users])

  const filtered = useMemo(() => {
    return users.filter(u => {
      const q = search.toLowerCase()
      const matchSearch =
        !q ||
        (u.username ?? '').toLowerCase().includes(q) ||
        (u.email ?? '').toLowerCase().includes(q) ||
        (u.fullName ?? '').toLowerCase().includes(q)
      const matchRole =
        filterRole === 'all' || (u.roles ?? []).includes(filterRole)
      const matchStatus =
        filterStatus === 'all' ||
        (filterStatus === 'active' && u.enabled) ||
        (filterStatus === 'inactive' && !u.enabled)
      return matchSearch && matchRole && matchStatus
    })
  }, [users, search, filterRole, filterStatus])

  return (
    <Box>
      {/* ── Toolbar ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <TextField
          placeholder="Tìm theo username, email, họ tên..."
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

        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Role</InputLabel>
          <Select
            value={filterRole}
            label="Role"
            onChange={e => setFilterRole(e.target.value)}
            startAdornment={<FilterListIcon sx={{ fontSize: 16, color: '#94a3b8', mr: 0.5 }} />}
          >
            <MenuItem value="all">Tất cả</MenuItem>
            {roles.map(r => <MenuItem key={r} value={r}>{r}</MenuItem>)}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 130 }}>
          <InputLabel>Trạng thái</InputLabel>
          <Select
            value={filterStatus}
            label="Trạng thái"
            onChange={e => setStatus(e.target.value)}
          >
            <MenuItem value="all">Tất cả</MenuItem>
            <MenuItem value="active">Active</MenuItem>
            <MenuItem value="inactive">Inactive</MenuItem>
          </Select>
        </FormControl>

        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
          {filtered.length} / {users.length} users
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
              <TableCell sx={{ width: 40 }} />
              <TableCell>Username</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Roles</TableCell>
              <TableCell>Provider</TableCell>
              <TableCell align="center">Status</TableCell>
              <TableCell align="right">Hành động</TableCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {loading ? (
              <SkeletonRows count={5} />
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <PeopleIcon sx={{ fontSize: 36, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy user nào
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((user) => (
                <TableRow
                  key={user.id}
                  sx={{ opacity: user.enabled ? 1 : 0.55 }}
                >
                  {/* Avatar */}
                  <TableCell sx={{ pl: 2 }}>
                    <UserAvatar username={user.username} email={user.email} />
                  </TableCell>

                  {/* Username */}
                  <TableCell>
                    <Typography
                      variant="body2"
                      sx={{
                        fontFamily: "'JetBrains Mono', monospace",
                        fontWeight: 600,
                        color: '#0f172a',
                        fontSize: '0.8rem',
                      }}
                    >
                      {user.username}
                    </Typography>
                    {user.fullName && (
                      <Typography variant="caption" color="text.secondary">
                        {user.fullName}
                      </Typography>
                    )}
                  </TableCell>

                  {/* Email */}
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.8rem' }}>
                      {user.email ?? '—'}
                    </Typography>
                  </TableCell>

                  {/* Roles */}
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {(user.roles ?? []).length === 0 ? (
                        <Typography variant="caption" color="text.disabled">—</Typography>
                      ) : (
                        (user.roles ?? []).map(r => <RoleChip key={r} role={r} />)
                      )}
                    </Box>
                  </TableCell>

                  {/* Provider */}
                  <TableCell>
                    <Chip
                      label={user.provider ?? 'LOCAL'}
                      size="small"
                      sx={{
                        height: 20,
                        fontSize: '0.63rem',
                        fontWeight: 600,
                        backgroundColor: user.provider === 'GOOGLE' ? '#e0f2fe' : '#f1f5f9',
                        color: user.provider === 'GOOGLE' ? '#0284c7' : '#64748b',
                        '& .MuiChip-label': { px: '6px' },
                      }}
                    />
                  </TableCell>

                  {/* Status */}
                  <TableCell align="center">
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}>
                      <StatusChip enabled={user.enabled} />
                      <Switch
                        checked={user.enabled ?? false}
                        onChange={e => onToggle(user.id, e.target.checked)}
                        size="small"
                        sx={{ mt: 0.25 }}
                      />
                    </Box>
                  </TableCell>

                  {/* Actions */}
                  <TableCell align="right">
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                      <Tooltip title="Chỉnh sửa" arrow>
                        <IconButton
                          size="small"
                          onClick={() => onEdit(user)}
                          sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}
                        >
                          <EditIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Xóa user" arrow>
                        <IconButton
                          size="small"
                          onClick={() => onDelete(user)}
                          sx={{ color: '#dc2626', '&:hover': { backgroundColor: '#fee2e2' } }}
                        >
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
