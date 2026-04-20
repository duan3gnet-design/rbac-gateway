import { Chip } from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline'

export default function StatusChip({ enabled }) {
  return enabled ? (
    <Chip
      icon={<CheckCircleOutlineIcon sx={{ fontSize: '13px !important' }} />}
      label="Active"
      size="small"
      sx={{
        backgroundColor: '#dcfce7',
        color: '#16a34a',
        fontWeight: 600,
        fontSize: '0.7rem',
        height: 22,
        '& .MuiChip-icon': { color: '#16a34a', ml: '6px' },
        '& .MuiChip-label': { pl: '4px', pr: '8px' },
      }}
    />
  ) : (
    <Chip
      icon={<PauseCircleOutlineIcon sx={{ fontSize: '13px !important' }} />}
      label="Inactive"
      size="small"
      sx={{
        backgroundColor: '#f1f5f9',
        color: '#94a3b8',
        fontWeight: 600,
        fontSize: '0.7rem',
        height: 22,
        '& .MuiChip-icon': { color: '#94a3b8', ml: '6px' },
        '& .MuiChip-label': { pl: '4px', pr: '8px' },
      }}
    />
  )
}
