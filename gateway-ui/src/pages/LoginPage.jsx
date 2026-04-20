import { useState } from 'react'
import {
  Box, Button, TextField, Typography, Paper,
  Alert, CircularProgress, InputAdornment, IconButton,
} from '@mui/material'
import HubIcon from '@mui/icons-material/HubRounded'
import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import axios from 'axios'

export default function LoginPage({ onLogin }) {
  const [form, setForm]       = useState({ username: '', password: '' })
  const [showPwd, setShowPwd] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  const handleSubmit = async () => {
    if (!form.username || !form.password) {
      setError('Vui lòng nhập đầy đủ thông tin')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await axios.post('/api/auth/login', {
        username: form.username,
        password: form.password,
      })
      console.log('[Login] response:', res.data)  // debug
      const token = res.data?.token || res.data?.accessToken || res.data?.access_token
      if (!token) throw new Error('Không nhận được access token')
      localStorage.setItem('access_token', token)
      onLogin(token)
    } catch (e) {
      const status = e.response?.status
      const msg    = e.response?.data?.message || e.response?.data?.error || e.message
      if (status === 401 || status === 403) {
        setError('Sai tên đăng nhập hoặc mật khẩu')
      } else if (status === 400) {
        setError('Dữ liệu không hợp lệ: ' + msg)
      } else if (!status) {
        setError('Không thể kết nối đến server. Kiểm tra api-gateway đang chạy không?')
      } else {
        setError(msg || 'Lỗi đăng nhập')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleKey = (e) => { if (e.key === 'Enter') handleSubmit() }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 60%, #0f172a 100%)',
      }}
    >
      {/* Decorative blobs */}
      <Box sx={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none' }}>
        <Box sx={{ position: 'absolute', top: '15%', left: '10%', width: 300, height: 300, borderRadius: '50%', background: 'rgba(59,91,219,0.08)', filter: 'blur(80px)' }} />
        <Box sx={{ position: 'absolute', bottom: '20%', right: '8%', width: 250, height: 250, borderRadius: '50%', background: 'rgba(124,58,237,0.08)', filter: 'blur(80px)' }} />
      </Box>

      <Paper
        elevation={0}
        sx={{
          width: 400,
          p: 4,
          borderRadius: 4,
          border: '1px solid rgba(255,255,255,0.08)',
          backgroundColor: 'rgba(30,41,59,0.95)',
          backdropFilter: 'blur(20px)',
          position: 'relative',
          zIndex: 1,
        }}
      >
        {/* Logo */}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 4 }}>
          <Box
            sx={{
              width: 52, height: 52, borderRadius: 3,
              background: 'linear-gradient(135deg, #3b5bdb 0%, #7c3aed 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              mb: 2, boxShadow: '0 8px 24px rgba(59,91,219,0.4)',
            }}
          >
            <HubIcon sx={{ color: '#fff', fontSize: 28 }} />
          </Box>
          <Typography variant="h5" sx={{ color: '#f1f5f9', fontWeight: 700 }}>
            RBAC Gateway
          </Typography>
          <Typography variant="body2" sx={{ color: '#64748b', mt: 0.5 }}>
            Admin Console — Đăng nhập để tiếp tục
          </Typography>
        </Box>

        {/* Form */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {error && (
            <Alert severity="error" sx={{ borderRadius: 2, fontSize: '0.82rem' }}>
              {error}
            </Alert>
          )}

          <TextField
            label="Tên đăng nhập"
            value={form.username}
            onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
            onKeyDown={handleKey}
            autoComplete="username"
            autoFocus
            fullWidth
            sx={{
              '& .MuiOutlinedInput-root': {
                color: '#f1f5f9',
                '& fieldset': { borderColor: 'rgba(255,255,255,0.12)' },
                '&:hover fieldset': { borderColor: 'rgba(255,255,255,0.25)' },
                '&.Mui-focused fieldset': { borderColor: '#3b5bdb' },
              },
              '& .MuiInputLabel-root': { color: '#64748b' },
              '& .MuiInputLabel-root.Mui-focused': { color: '#748ffc' },
            }}
          />

          <TextField
            label="Mật khẩu"
            type={showPwd ? 'text' : 'password'}
            value={form.password}
            onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
            onKeyDown={handleKey}
            autoComplete="current-password"
            fullWidth
            slotProps={{
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPwd(v => !v)}
                      edge="end"
                      sx={{ color: '#64748b' }}
                    >
                      {showPwd
                        ? <VisibilityOffIcon fontSize="small" />
                        : <VisibilityIcon fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
            sx={{
              '& .MuiOutlinedInput-root': {
                color: '#f1f5f9',
                '& fieldset': { borderColor: 'rgba(255,255,255,0.12)' },
                '&:hover fieldset': { borderColor: 'rgba(255,255,255,0.25)' },
                '&.Mui-focused fieldset': { borderColor: '#3b5bdb' },
              },
              '& .MuiInputLabel-root': { color: '#64748b' },
              '& .MuiInputLabel-root.Mui-focused': { color: '#748ffc' },
            }}
          />

          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={loading}
            fullWidth
            size="large"
            sx={{
              mt: 1,
              py: 1.4,
              background: 'linear-gradient(135deg, #3b5bdb 0%, #7c3aed 100%)',
              '&:hover': { background: 'linear-gradient(135deg, #2f4ac0 0%, #6d28c9 100%)' },
              '&:disabled': { opacity: 0.6 },
              fontWeight: 600,
              fontSize: '0.95rem',
            }}
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
          >
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </Button>
        </Box>

        <Typography
          variant="caption"
          sx={{ display: 'block', textAlign: 'center', color: '#475569', mt: 3 }}
        >
          Yêu cầu quyền ROLE_ADMIN để truy cập
        </Typography>
      </Paper>
    </Box>
  )
}
