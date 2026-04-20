import { Chip, Tooltip } from '@mui/material'

const METHOD_CONFIG = {
  GET:    { color: '#16a34a', bg: '#dcfce7', label: 'GET' },
  POST:   { color: '#0284c7', bg: '#e0f2fe', label: 'POST' },
  PUT:    { color: '#d97706', bg: '#fef3c7', label: 'PUT' },
  PATCH:  { color: '#7c3aed', bg: '#ede9fe', label: 'PATCH' },
  DELETE: { color: '#dc2626', bg: '#fee2e2', label: 'DELETE' },
  ANY:    { color: '#64748b', bg: '#f1f5f9', label: 'ANY' },
}

export default function MethodChip({ method }) {
  const cfg = METHOD_CONFIG[method?.toUpperCase()] ?? METHOD_CONFIG.ANY
  return (
    <Chip
      label={cfg.label}
      size="small"
      sx={{
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontWeight: 700,
        fontSize: '0.68rem',
        height: 20,
        letterSpacing: '0.03em',
        fontFamily: "'JetBrains Mono', monospace",
        '& .MuiChip-label': { px: '6px' },
      }}
    />
  )
}

export function MethodChipGroup({ methods = [] }) {
  if (!methods.length || (methods.length === 1 && methods[0] === 'ANY')) {
    return <MethodChip method="ANY" />
  }
  if (methods.length <= 2) {
    return (
      <span className="flex gap-1 flex-wrap">
        {methods.map(m => <MethodChip key={m} method={m} />)}
      </span>
    )
  }
  return (
    <Tooltip title={methods.join(', ')} arrow>
      <span className="flex gap-1 flex-wrap items-center">
        {methods.slice(0, 2).map(m => <MethodChip key={m} method={m} />)}
        <Chip
          label={`+${methods.length - 2}`}
          size="small"
          sx={{ height: 20, fontSize: '0.68rem', '& .MuiChip-label': { px: '6px' } }}
        />
      </span>
    </Tooltip>
  )
}
