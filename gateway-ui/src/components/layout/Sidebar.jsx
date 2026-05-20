import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText,
  Typography, Divider, Tooltip, IconButton,
} from '@mui/material'
import RouteIcon from '@mui/icons-material/AltRoute'
import DashboardIcon from '@mui/icons-material/GridViewRounded'
import ShieldIcon from '@mui/icons-material/ShieldRounded'
import PeopleIcon from '@mui/icons-material/PeopleRounded'
import SettingsIcon from '@mui/icons-material/SettingsRounded'
import SpeedIcon from '@mui/icons-material/SpeedRounded'
import CategoryIcon from '@mui/icons-material/CategoryRounded'
import GroupWorkIcon from '@mui/icons-material/GroupWorkRounded'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import HubIcon from '@mui/icons-material/HubRounded'
import LogoutIcon from '@mui/icons-material/LogoutRounded'

const NAV_SECTIONS = [
  {
    title: 'Gateway',
    items: [
      { label: 'Dashboard',  icon: <DashboardIcon />, path: '/',            disabled: true  },
      { label: 'Routes',     icon: <RouteIcon />,     path: '/routes',      disabled: false },
      { label: 'Rate Limit', icon: <SpeedIcon />,     path: '/rate-limits', disabled: false },
    ],
  },
  {
    title: 'RBAC',
    items: [
      { label: 'Resources',   icon: <CategoryIcon />,  path: '/resources',   disabled: false },
      { label: 'Roles',       icon: <GroupWorkIcon />, path: '/roles',       disabled: false },
      { label: 'Permissions', icon: <ShieldIcon />,    path: '/permissions', disabled: false },
      { label: 'Users',       icon: <PeopleIcon />,    path: '/users',       disabled: false },
    ],
  },
  {
    title: 'System',
    items: [
      { label: 'Settings', icon: <SettingsIcon />, path: '/settings', disabled: true },
    ],
  },
]

function NavItem({ item, collapsed, activePath }) {
  const navigate = useNavigate()
  const active   = activePath === item.path ||
    (item.path !== '/' && activePath.startsWith(item.path))

  const btn = (
    <ListItem disablePadding sx={{ mb: 0.5 }}>
      <ListItemButton
        disabled={item.disabled}
        onClick={() => !item.disabled && navigate(item.path)}
        sx={{
          borderRadius: 2,
          px: collapsed ? 1.5 : 2,
          py: 0.875,
          minHeight: 40,
          justifyContent: collapsed ? 'center' : 'flex-start',
          backgroundColor: active ? 'rgba(59,91,219,0.18)' : 'transparent',
          '&:hover': {
            backgroundColor: active ? 'rgba(59,91,219,0.22)' : 'rgba(255,255,255,0.05)',
          },
          '&.Mui-disabled': { opacity: 0.35 },
        }}
      >
        <ListItemIcon sx={{
          minWidth: 0, mr: collapsed ? 0 : 1.5,
          color: active ? '#748ffc' : '#64748b',
          '& svg': { fontSize: 19 },
        }}>
          {item.icon}
        </ListItemIcon>
        {!collapsed && (
          <ListItemText
            primary={item.label}
            primaryTypographyProps={{
              fontSize: '0.875rem',
              fontWeight: active ? 600 : 400,
              color: active ? '#e2e8f0' : '#94a3b8',
            }}
          />
        )}
      </ListItemButton>
    </ListItem>
  )

  return collapsed && !item.disabled ? (
    <Tooltip key={item.label} title={item.label} placement="right" arrow>
      <span>{btn}</span>
    </Tooltip>
  ) : btn
}

export default function Sidebar({ onLogout }) {
  const [collapsed, setCollapsed] = useState(false)
  const location   = useLocation()
  const activePath = location.pathname
  const width      = collapsed ? 64 : 240

  return (
    <Box sx={{
      width,
      minHeight: '100vh',
      backgroundColor: '#0f172a',
      display: 'flex',
      flexDirection: 'column',
      transition: 'width 0.22s ease',
      overflow: 'hidden',
      flexShrink: 0,
      position: 'sticky',
      top: 0,
      height: '100vh',
    }}>
      {/* ── Logo ── */}
      <Box sx={{
        px: collapsed ? 1.5 : 2.5, py: 2.5,
        display: 'flex', alignItems: 'center', gap: 1.5, minHeight: 64,
      }}>
        <Box sx={{
          width: 36, height: 36, borderRadius: 2, flexShrink: 0,
          background: 'linear-gradient(135deg, #3b5bdb 0%, #7c3aed 100%)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <HubIcon sx={{ color: '#fff', fontSize: 20 }} />
        </Box>
        {!collapsed && (
          <Box>
            <Typography sx={{ color: '#f1f5f9', fontWeight: 700, fontSize: '0.95rem', lineHeight: 1.2 }}>
              RBAC Gateway
            </Typography>
            <Typography sx={{ color: '#64748b', fontSize: '0.7rem' }}>
              Admin Console
            </Typography>
          </Box>
        )}
      </Box>

      <Divider sx={{ borderColor: '#1e293b' }} />

      {/* ── Nav sections ── */}
      <Box sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', px: 1, py: 1.5 }}>
        {NAV_SECTIONS.map((section, si) => (
          <Box key={section.title}>
            {si > 0 && <Divider sx={{ borderColor: '#1e293b', my: 1 }} />}
            {!collapsed && (
              <Typography sx={{
                color: '#334155', fontSize: '0.65rem', fontWeight: 700,
                letterSpacing: '0.08em', textTransform: 'uppercase',
                px: 2, pb: 0.75, pt: si === 0 ? 0 : 0.5,
              }}>
                {section.title}
              </Typography>
            )}
            <List disablePadding>
              {section.items.map(item => (
                <NavItem
                  key={item.label}
                  item={item}
                  collapsed={collapsed}
                  activePath={activePath}
                />
              ))}
            </List>
          </Box>
        ))}
      </Box>

      <Divider sx={{ borderColor: '#1e293b' }} />

      {/* ── Logout ── */}
      <Box sx={{ p: 1 }}>
        <Tooltip title="Đăng xuất" placement="right">
          <ListItemButton
            onClick={onLogout}
            sx={{
              borderRadius: 2,
              px: collapsed ? 1.5 : 2, py: 1,
              justifyContent: collapsed ? 'center' : 'flex-start',
              '&:hover': { backgroundColor: 'rgba(220,38,38,0.12)' },
            }}
          >
            <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 1.5, color: '#64748b', '& svg': { fontSize: 20 } }}>
              <LogoutIcon />
            </ListItemIcon>
            {!collapsed && (
              <ListItemText
                primary="Đăng xuất"
                primaryTypographyProps={{ fontSize: '0.875rem', color: '#94a3b8' }}
              />
            )}
          </ListItemButton>
        </Tooltip>
      </Box>

      {/* ── Collapse toggle ── */}
      <Box sx={{ display: 'flex', justifyContent: collapsed ? 'center' : 'flex-end', p: 1 }}>
        <Tooltip title={collapsed ? 'Mở rộng' : 'Thu gọn'} placement="right">
          <IconButton
            size="small"
            onClick={() => setCollapsed(c => !c)}
            sx={{ color: '#475569', '&:hover': { color: '#94a3b8', backgroundColor: 'rgba(255,255,255,0.05)' } }}
          >
            {collapsed ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
          </IconButton>
        </Tooltip>
      </Box>
    </Box>
  )
}
