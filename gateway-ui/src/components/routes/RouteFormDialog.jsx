import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography,
  Box, Divider, InputAdornment, FormControlLabel, Switch,
  Alert, CircularProgress, Autocomplete, Chip, Tooltip,
  IconButton,
} from '@mui/material'
import LinkIcon from '@mui/icons-material/LinkRounded'
import CodeIcon from '@mui/icons-material/CodeRounded'
import SortIcon from '@mui/icons-material/SortByAlphaRounded'
import RefreshIcon from '@mui/icons-material/RefreshRounded'
import CloudIcon from '@mui/icons-material/CloudRounded'
import { eurekaApi } from '../../api/routeApi'

const DEFAULT_FORM = {
  id: '',
  uri: '',
  predicates: '[]',
  filters: '[]',
  routeOrder: 0,
  enabled: true,
}

const PREDICATE_TEMPLATES = [
  {
    label: 'Path + Method (GET)',
    value: '[{"name":"Path","args":{"pattern":"/api/example/**"}},{"name":"Method","args":{"methods":"GET"}}]',
  },
  {
    label: 'Path + Method (POST)',
    value: '[{"name":"Path","args":{"pattern":"/api/example"}},{"name":"Method","args":{"methods":"POST"}}]',
  },
  {
    label: 'Path only',
    value: '[{"name":"Path","args":{"pattern":"/api/example/**"}}]',
  },
]

const FILTER_TEMPLATES = [
  {
    label: 'CircuitBreaker (fastOpenCB)',
    value: '[{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth","statusCodes":"500,502,503,504"}}]',
  },
  {
    label: 'CircuitBreaker (slowOpenCB)',
    value: '[{"name":"CircuitBreaker","args":{"name":"slowOpenCB","fallbackUri":"forward:/fallback/resource","statusCodes":"500,502,503,504"}}]',
  },
  {
    label: 'Retry + CircuitBreaker',
    value: '[{"name":"Retry","args":{"retries":"3","statuses":"SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT","methods":"GET"}},{"name":"CircuitBreaker","args":{"name":"fastOpenCB","fallbackUri":"forward:/fallback/auth"}}]',
  },
  { label: 'Không có filter', value: '[]' },
]

export default function RouteFormDialog({ open, onClose, onSave, initialData, saving }) {
  const isEdit = Boolean(initialData?.id)
  const [form, setForm]       = useState(DEFAULT_FORM)
  const [errors, setErrors]   = useState({})
  const [jsonError, setJsonError] = useState({ predicates: '', filters: '' })

  // ── Eureka state ────────────────────────────────────────────────────────────
  const [eurekaServices, setEurekaServices]   = useState([])
  const [eurekaLoading, setEurekaLoading]     = useState(false)
  const [eurekaError, setEurekaError]         = useState('')
  const [selectedService, setSelectedService] = useState(null)

  // ── Fetch Eureka services ───────────────────────────────────────────────────
  const fetchEurekaServices = async () => {
    setEurekaLoading(true)
    setEurekaError('')
    try {
      const res = await eurekaApi.getServices()
      setEurekaServices(res.data ?? [])
    } catch (e) {
      setEurekaError(e.response?.data?.message || 'Không thể lấy danh sách service từ Eureka')
    } finally {
      setEurekaLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      setForm(initialData ?? DEFAULT_FORM)
      setErrors({})
      setJsonError({ predicates: '', filters: '' })
      setSelectedService(null)
      fetchEurekaServices()
    }
  }, [open, initialData])

  // ── Khi chọn service từ Eureka → tự điền URI ───────────────────────────────
  const handleSelectEurekaService = (_, svc) => {
    setSelectedService(svc)
    if (svc) {
      const uri = `http://${svc.serviceId.toLowerCase()}:${svc.port}`
      setForm(f => ({ ...f, uri }))
      if (errors.uri) setErrors(e => ({ ...e, uri: '' }))
    }
  }

  const set = (field) => (e) => {
    const val = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm(f => ({ ...f, [field]: val }))
    if (errors[field]) setErrors(e => ({ ...e, [field]: '' }))

    // Validate JSON inline
    if (field === 'predicates' || field === 'filters') {
      try {
        JSON.parse(val)
        setJsonError(j => ({ ...j, [field]: '' }))
      } catch {
        setJsonError(j => ({ ...j, [field]: 'JSON không hợp lệ' }))
      }
    }
    // Nếu user tự sửa URI → bỏ chọn Eureka service
    if (field === 'uri') setSelectedService(null)
  }

  const validate = () => {
    const e = {}
    if (!form.id.trim()) e.id = 'Route ID là bắt buộc'
    if (!/^[a-z0-9-]+$/.test(form.id)) e.id = 'Chỉ dùng chữ thường, số và dấu gạch ngang'
    if (!form.uri.trim()) e.uri = 'URI là bắt buộc'
    try { new URL(form.uri) } catch { e.uri = 'URI không hợp lệ (ví dụ: http://localhost:8081)' }
    try { JSON.parse(form.predicates) } catch { e.predicates = 'JSON không hợp lệ' }
    try { JSON.parse(form.filters) } catch { e.filters = 'JSON không hợp lệ' }
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = () => {
    if (!validate()) return
    onSave({ ...form, routeOrder: Number(form.routeOrder) })
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Typography variant="h6">{isEdit ? 'Chỉnh sửa Route' : 'Thêm Route mới'}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {isEdit ? `Đang sửa: ${initialData.id}` : 'Điền thông tin route cần thêm vào gateway'}
        </Typography>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5 }}>
        <Grid container spacing={2.5}>
          {/* ── Route ID ── */}
          <Grid size={6}>
            <TextField
              label="Route ID"
              value={form.id}
              onChange={set('id')}
              disabled={isEdit}
              error={Boolean(errors.id)}
              helperText={errors.id || 'Ví dụ: auth-login, resource-service'}
              fullWidth
              slotProps={{
                htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } }
              }}
            />
          </Grid>

          {/* ── Route Order ── */}
          <Grid size={3}>
            <TextField
              label="Order"
              type="number"
              value={form.routeOrder}
              onChange={set('routeOrder')}
              fullWidth
              slotProps={{
                input: {
                  startAdornment: <InputAdornment position="start"><SortIcon sx={{ fontSize: 16, color: '#94a3b8' }} /></InputAdornment>
                }
              }}
            />
          </Grid>

          {/* ── Enabled ── */}
          <Grid size={3} sx={{ display: 'flex', alignItems: 'center' }}>
            <FormControlLabel
              control={
                <Switch
                  checked={form.enabled}
                  onChange={set('enabled')}
                  color="primary"
                />
              }
              label={
                <Typography variant="body2" fontWeight={500}>
                  {form.enabled ? 'Đang bật' : 'Đang tắt'}
                </Typography>
              }
            />
          </Grid>

          {/* ── Eureka Service Picker ── */}
          <Grid size={12}>
            <Box sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
              <CloudIcon sx={{ fontSize: 16, color: '#64748b' }} />
              <Typography variant="subtitle2">Chọn service từ Eureka</Typography>
              <Tooltip title="Làm mới danh sách">
                <span>
                  <IconButton
                    size="small"
                    onClick={fetchEurekaServices}
                    disabled={eurekaLoading}
                    sx={{ ml: 0.5 }}
                  >
                    {eurekaLoading
                      ? <CircularProgress size={14} />
                      : <RefreshIcon sx={{ fontSize: 16 }} />
                    }
                  </IconButton>
                </span>
              </Tooltip>
              {selectedService && (
                <Chip
                  label={selectedService.serviceId}
                  size="small"
                  color="primary"
                  variant="outlined"
                  onDelete={() => {
                    setSelectedService(null)
                    setForm(f => ({ ...f, uri: '' }))
                  }}
                  sx={{ ml: 'auto', fontSize: '0.72rem' }}
                />
              )}
            </Box>

            <Autocomplete
              options={eurekaServices}
              value={selectedService}
              onChange={handleSelectEurekaService}
              loading={eurekaLoading}
              getOptionLabel={(svc) => {
                const url = svc.homePageUrl?.replace(/\/$/, '') || `http://${svc.ipAddr}:${svc.port}`
                return `${svc.serviceId}  —  ${url}`
              }}
              isOptionEqualToValue={(a, b) => a.instanceId === b.instanceId}
              noOptionsText={eurekaError || 'Không có service nào đang chạy'}
              renderOption={(props, svc) => {
                const url = svc.homePageUrl?.replace(/\/$/, '') || `http://${svc.ipAddr}:${svc.port}`
                return (
                  <Box component="li" {...props} key={svc.instanceId}>
                    <Box>
                      <Typography variant="body2" fontWeight={600} sx={{ lineHeight: 1.4 }}>
                        {svc.serviceId}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "'JetBrains Mono', monospace" }}>
                        {url}
                      </Typography>
                    </Box>
                  </Box>
                )
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  placeholder="Tìm theo tên service..."
                  size="small"
                  error={Boolean(eurekaError)}
                  helperText={eurekaError || 'Chọn để tự điền URI, hoặc nhập thủ công bên dưới'}
                  slotProps={{
                    input: {
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {eurekaLoading ? <CircularProgress size={14} /> : null}
                          {params.InputProps.endAdornment}
                        </>
                      ),
                    }
                  }}
                />
              )}
            />
          </Grid>

          {/* ── URI ── */}
          <Grid size={12}>
            <TextField
              label="Upstream URI"
              value={form.uri}
              onChange={set('uri')}
              error={Boolean(errors.uri)}
              helperText={errors.uri || 'URI của service phía sau gateway, ví dụ: http://localhost:8081'}
              fullWidth
              slotProps={{
                input: {
                  startAdornment: <InputAdornment position="start"><LinkIcon sx={{ fontSize: 16, color: '#94a3b8' }} /></InputAdornment>
                },
                htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } }
              }}
            />
          </Grid>

          {/* ── Predicates ── */}
          <Grid size={12}>
            <Box sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
              <CodeIcon sx={{ fontSize: 16, color: '#64748b' }} />
              <Typography variant="subtitle2">Predicates</Typography>
              <Box sx={{ display: 'flex', gap: 0.5, ml: 'auto', flexWrap: 'wrap' }}>
                {PREDICATE_TEMPLATES.map(t => (
                  <Button
                    key={t.label}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.68rem', py: 0.25, px: 1, borderRadius: 1, borderColor: '#e2e8f0', color: '#64748b' }}
                    onClick={() => setForm(f => ({ ...f, predicates: t.value }))}
                  >
                    {t.label}
                  </Button>
                ))}
              </Box>
            </Box>
            <TextField
              value={form.predicates}
              onChange={set('predicates')}
              error={Boolean(errors.predicates || jsonError.predicates)}
              helperText={errors.predicates || jsonError.predicates || 'JSON array các predicate: Path, Method, Header...'}
              fullWidth
              multiline
              minRows={3}
              slotProps={{
                htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem' } }
              }}
            />
          </Grid>

          {/* ── Filters ── */}
          <Grid size={12}>
            <Box sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
              <CodeIcon sx={{ fontSize: 16, color: '#64748b' }} />
              <Typography variant="subtitle2">Filters</Typography>
              <Box sx={{ display: 'flex', gap: 0.5, ml: 'auto', flexWrap: 'wrap' }}>
                {FILTER_TEMPLATES.map(t => (
                  <Button
                    key={t.label}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.68rem', py: 0.25, px: 1, borderRadius: 1, borderColor: '#e2e8f0', color: '#64748b' }}
                    onClick={() => setForm(f => ({ ...f, filters: t.value }))}
                  >
                    {t.label}
                  </Button>
                ))}
              </Box>
            </Box>
            <TextField
              value={form.filters}
              onChange={set('filters')}
              error={Boolean(errors.filters || jsonError.filters)}
              helperText={errors.filters || jsonError.filters || 'JSON array các filter: CircuitBreaker, Retry, AddRequestHeader...'}
              fullWidth
              multiline
              minRows={3}
              slotProps={{
                htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem' } }
              }}
            />
          </Grid>

          {Object.keys(errors).length > 0 && (
            <Grid size={12}>
              <Alert severity="error" sx={{ borderRadius: 2 }}>
                Vui lòng kiểm tra lại các trường bị lỗi trước khi lưu.
              </Alert>
            </Grid>
          )}
        </Grid>
      </DialogContent>

      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button onClick={onClose} disabled={saving} color="inherit">
          Hủy
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={saving || Boolean(jsonError.predicates || jsonError.filters)}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
        >
          {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật' : 'Tạo Route'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
