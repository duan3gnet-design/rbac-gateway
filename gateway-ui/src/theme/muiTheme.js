import { createTheme } from '@mui/material/styles'

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#3b5bdb',
      light: '#748ffc',
      dark: '#2f4ac0',
      contrastText: '#fff',
    },
    secondary: { main: '#7c3aed' },
    success: { main: '#16a34a', light: '#dcfce7' },
    error: { main: '#dc2626', light: '#fee2e2' },
    warning: { main: '#d97706', light: '#fef3c7' },
    info: { main: '#0284c7', light: '#e0f2fe' },
    background: { default: '#f8fafc', paper: '#ffffff' },
    text: { primary: '#0f172a', secondary: '#64748b' },
    divider: '#e2e8f0',
  },
  typography: {
    fontFamily: "'Inter', sans-serif",
    h4: { fontWeight: 700, letterSpacing: '-0.5px' },
    h5: { fontWeight: 600, letterSpacing: '-0.3px' },
    h6: { fontWeight: 600 },
    subtitle2: { fontWeight: 500, color: '#64748b' },
  },
  shape: { borderRadius: 10 },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 500, borderRadius: 8 },
        containedPrimary: {
          boxShadow: '0 1px 3px rgba(59,91,219,0.3)',
          '&:hover': { boxShadow: '0 2px 8px rgba(59,91,219,0.4)' },
        },
      },
    },
    MuiTextField: {
      defaultProps: { size: 'small' },
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': { borderRadius: 8, backgroundColor: '#fff' },
        },
      },
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            backgroundColor: '#f1f5f9',
            fontWeight: 600,
            fontSize: '0.72rem',
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
            color: '#64748b',
            borderBottom: '1px solid #e2e8f0',
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderBottom: '1px solid #f1f5f9', padding: '10px 16px' },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&:hover': { backgroundColor: '#f8fafc' },
          '&:last-child td': { borderBottom: 0 },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, fontSize: '0.7rem' },
      },
    },
    MuiDialog: {
      styleOverrides: { paper: { borderRadius: 14 } },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: '#1e293b', fontSize: '0.73rem', borderRadius: 6 },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: { backgroundColor: '#0f172a', color: '#e2e8f0', borderRight: 'none' },
      },
    },
  },
})

export default theme
