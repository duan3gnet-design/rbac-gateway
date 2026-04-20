import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { Box } from '@mui/material'
import theme from './theme/muiTheme'
import Sidebar from './components/layout/Sidebar'
import RoutesPage from './pages/RoutesPage'
import RateLimitPage from './pages/RateLimitPage'
import LoginPage from './pages/LoginPage'
import './index.css'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('access_token'))

  const handleLogin = (newToken) => setToken(newToken)

  const handleLogout = () => {
    localStorage.removeItem('access_token')
    setToken(null)
  }

  if (!token) {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <LoginPage onLogin={handleLogin} />
      </ThemeProvider>
    )
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f8fafc' }}>
          <Sidebar onLogout={handleLogout} />
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
            <Routes>
              <Route path="/" element={<Navigate to="/routes" replace />} />
              <Route path="/routes" element={<RoutesPage />} />
              <Route path="/rate-limits" element={<RateLimitPage />} />
            </Routes>
          </Box>
        </Box>
      </BrowserRouter>
    </ThemeProvider>
  )
}
