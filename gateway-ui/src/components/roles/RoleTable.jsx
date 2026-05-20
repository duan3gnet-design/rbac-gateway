import {
  Box, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, IconButton, Tooltip,
  Typography, Chip, Skeleton,
} from '@mui/material'
import EditIcon from '@mui/icons-material/EditRounded'
import DeleteIcon from '@mui/icons-material/DeleteRounded'
import KeyIcon from '@mui/icons-material/KeyRounded'
import GroupWorkIcon from '@mui/icons-material/GroupWorkRounded'

const ACTION_COLORS = {
  READ:   { bg: '#e0f2fe', color: '#0284c7' },
  CREATE: { bg: '#dcfce7', color: '#16a34a' },
  UPDATE: { bg: '#fef3c7', color: '#d97706' },
  DELETE: { bg: '#fee2e2', color: '#dc2626' },
  WRITE:  { bg: '#dcfce7', color: '#16a34a' },
  MANAGE: { bg: '#f3e8ff', color: '#7c3aed' },
}

function PermissionChip({ code }) {
  const [, action] = (code ?? '').split(':')
  const cfg = ACTION_COLORS[action] ?? { bg: '#f1f5f9', color: '#475569' }
  return (
    <Chip
      label={code}
      size="small"
      sx={{
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: '0.63rem',
        fontWeight: 700,
        height: 20,
        '& .MuiChip-label': { px: '7px' },
      }}
    />
  )
}

function SkeletonRows({ count = 4 }) {
  return Array.from({ length: count }).map((_, i) => (
    <TableRow key={i}>
      {[60, 150, 300, 80, 100].map((w, j) => (
        <TableCell key={j}>
          <Skeleton variant="rounded" width={w} height={20} sx={{ borderRadius: 1 }} />
        </TableCell>
      ))}
    </TableRow>
  ))
}

export default function RoleTable({ roles, loading, onEdit, onDelete, onAssign }) {
  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{ border: '1px solid #e2e8f0', borderRadius: 3, overflow: 'hidden' }}
    >
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: 70 }}>ID</TableCell>
            <TableCell sx={{ width: 200 }}>Role Name</TableCell>
            <TableCell>Permissions</TableCell>
            <TableCell align="center" sx={{ width: 90 }}>Users</TableCell>
            <TableCell align="right" sx={{ width: 120 }}>Hành động</TableCell>
          </TableRow>
        </TableHead>

        <TableBody>
          {loading ? (
            <SkeletonRows count={4} />
          ) : roles.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                <GroupWorkIcon sx={{ fontSize: 36, color: '#e2e8f0', mb: 1, display: 'block', mx: 'auto' }} />
                <Typography variant="body2" color="text.secondary">
                  Chưa có role nào
                </Typography>
              </TableCell>
            </TableRow>
          ) : (
            roles.map((role) => {
              const isAdmin = role.name.includes('ADMIN')
              return (
                <TableRow key={role.id}>
                  {/* ID */}
                  <TableCell>
                    <Typography variant="caption" color="text.disabled" sx={{ fontFamily: 'monospace' }}>
                      #{role.id}
                    </Typography>
                  </TableCell>

                  {/* Name */}
                  <TableCell>
                    <Chip
                      label={role.name}
                      size="small"
                      sx={{
                        backgroundColor: isAdmin ? '#f3e8ff' : '#f1f5f9',
                        color: isAdmin ? '#7c3aed' : '#475569',
                        fontFamily: "'JetBrains Mono', monospace",
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        height: 24,
                        '& .MuiChip-label': { px: '10px' },
                      }}
                    />
                  </TableCell>

                  {/* Permissions */}
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', maxWidth: 520 }}>
                      {(role.permissions ?? []).length === 0 ? (
                        <Typography variant="caption" color="text.disabled" sx={{ fontStyle: 'italic' }}>
                          chưa gán permission
                        </Typography>
                      ) : (
                        role.permissions.map(p => (
                          <PermissionChip key={p.id} code={p.code} />
                        ))
                      )}
                    </Box>
                  </TableCell>

                  {/* User count */}
                  <TableCell align="center">
                    <Chip
                      label={role.userCount}
                      size="small"
                      sx={{
                        backgroundColor: role.userCount > 0 ? '#f0fdf4' : '#f8fafc',
                        color: role.userCount > 0 ? '#16a34a' : '#94a3b8',
                        fontWeight: 700,
                        fontSize: '0.75rem',
                        height: 22,
                      }}
                    />
                  </TableCell>

                  {/* Actions */}
                  <TableCell align="right">
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                      <Tooltip title="Gán Permissions" arrow>
                        <IconButton
                          size="small"
                          onClick={() => onAssign(role)}
                          sx={{ color: '#16a34a', '&:hover': { backgroundColor: '#f0fdf4' } }}
                        >
                          <KeyIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Chỉnh sửa" arrow>
                        <IconButton
                          size="small"
                          onClick={() => onEdit(role)}
                          sx={{ color: '#3b5bdb', '&:hover': { backgroundColor: '#eef2ff' } }}
                        >
                          <EditIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          role.userCount > 0
                            ? `Không thể xóa — đang được dùng bởi ${role.userCount} user`
                            : 'Xóa role'
                        }
                        arrow
                      >
                        <span>
                          <IconButton
                            size="small"
                            onClick={() => onDelete(role)}
                            disabled={role.userCount > 0}
                            sx={{ color: '#dc2626', '&:hover': { backgroundColor: '#fee2e2' } }}
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
  )
}
