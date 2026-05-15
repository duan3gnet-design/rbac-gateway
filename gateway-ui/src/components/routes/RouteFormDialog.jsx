import { useState, useEffect } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Grid, Typography,
  Box, Divider, InputAdornment, FormControlLabel, Switch,
  Alert, CircularProgress, Autocomplete, Chip, Tooltip,
  IconButton, ToggleButtonGroup, ToggleButton, Paper,
} from '@mui/material'
import LinkIcon       from '@mui/icons-material/LinkRounded'
import CodeIcon       from '@mui/icons-material/CodeRounded'
import SortIcon       from '@mui/icons-material/SortByAlphaRounded'
import RefreshIcon    from '@mui/icons-material/RefreshRounded'
import CloudIcon      from '@mui/icons-material/CloudRounded'
import RouteIcon      from '@mui/icons-material/AltRouteRounded'
import AddIcon        from '@mui/icons-material/AddRounded'
import { eurekaApi }  from '../../api/routeApi'

// ─── Constants ───────────────────────────────────────────────────────────────

const DEFAULT_FORM = {
  id: '',
  uri: '',
  predicates: '[]',
  filters: '[]',
  routeOrder: 0,
  enabled: true,
}

const ALL_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

const METHOD_COLORS = {
  GET:    { bg: '#dcfce7', color: '#15803d', border: '#86efac' },
  POST:   { bg: '#dbeafe', color: '#1d4ed8', border: '#93c5fd' },
  PUT:    { bg: '#fef9c3', color: '#a16207', border: '#fde047' },
  PATCH:  { bg: '#ede9fe', color: '#6d28d9', border: '#c4b5fd' },
  DELETE: { bg: '#fee2e2', color: '#b91c1c', border: '#fca5a5' },
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

// ─── Helper: build predicate JSON từ path + methods ─────────────────────────

function buildPredicateJson(path, methods) {
  const predicates = [{ name: 'Path', args: { pattern: path } }]
  if (methods.length > 0) {
    predicates.push({ name: 'Method', args: { methods: methods.join(',') } })
  }
  return JSON.stringify(predicates, null, 2)
}

// ─── MethodChip ──────────────────────────────────────────────────────────────

function MethodChip({ method }) {
  const style = METHOD_COLORS[method] ?? { bg: '#f1f5f9', color: '#475569', border: '#cbd5e1' }
  return (
    <Box component="span" sx={{
      display: 'inline-block',
      px: 0.8, py: 0.1,
      borderRadius: 0.75,
      fontSize: '0.65rem',
      fontWeight: 700,
      fontFamily: "'JetBrains Mono', monospace",
      letterSpacing: '0.03em',
      border: `1px solid ${style.border}`,
      bgcolor: style.bg,
      color: style.color,
      lineHeight: 1.8,
    }}>
      {method}
    </Box>
  )
}

// ─── Component chính ─────────────────────────────────────────────────────────

export default function RouteFormDialog({ open, onClose, onSave, initialData, saving }) {
  const isEdit = Boolean(initialData?.id)

  const [form, setForm]           = useState(DEFAULT_FORM)
  const [errors, setErrors]       = useState({})
  const [jsonError, setJsonError] = useState({ predicates: '', filters: '' })

  // ── Eureka: service list ──────────────────────────────────────────────────
  const [eurekaServices, setEurekaServices]   = useState([])
  const [eurekaLoading, setEurekaLoading]     = useState(false)
  const [eurekaError, setEurekaError]         = useState('')
  const [selectedService, setSelectedService] = useState(null)

  // ── Eureka: mapping picker ────────────────────────────────────────────────
  const [mappings, setMappings]           = useState([])
  const [mappingsLoading, setMappingsLoading] = useState(false)
  const [mappingsError, setMappingsError] = useState('')
  const [selectedMapping, setSelectedMapping] = useState(null)
  const [selectedMethods, setSelectedMethods] = useState([])

  // ── Fetch services ────────────────────────────────────────────────────────
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

  // ── Fetch mappings khi chọn service ─────────────────────────────────────
  const fetchMappings = async (serviceId) => {
    setMappings([])
    setSelectedMapping(null)
    setSelectedMethods([])
    setMappingsError('')
    if (!serviceId) return
    setMappingsLoading(true)
    try {
      const res = await eurekaApi.getMappings(serviceId)
      setMappings(res.data ?? [])
    } catch (e) {
      setMappingsError(e.response?.data?.message || `Không thể lấy mappings từ ${serviceId}`)
    } finally {
      setMappingsLoading(false)
    }
  }

  useEffect(() => {
    if (open) {
      setForm(initialData ?? DEFAULT_FORM)
      setErrors({})
      setJsonError({ predicates: '', filters: '' })
      setSelectedService(null)
      setMappings([])
      setSelectedMapping(null)
      setSelectedMethods([])
      setMappingsError('')
      fetchEurekaServices()
    }
  }, [open, initialData])

  // ── Chọn service → điền URI + load mappings ──────────────────────────────
  const handleSelectEurekaService = (_, svc) => {
    setSelectedService(svc)
    if (svc) {
      const uri = `http://${svc.serviceId.toLowerCase()}:${svc.port}`
      setForm(f => ({ ...f, uri }))
      if (errors.uri) setErrors(e => ({ ...e, uri: '' }))
      fetchMappings(svc.serviceId)
    } else {
      setMappings([])
      setSelectedMapping(null)
      setSelectedMethods([])
    }
  }

  // ── Chọn path mapping ───────────────────────────────────────────────────
  const handleSelectMapping = (_, mapping) => {
    setSelectedMapping(mapping)
    if (mapping) {
      // Pre-fill methods từ mapping, user có thể chỉnh lại
      setSelectedMethods(mapping.methods ?? [])
    }
  }

  // ── Toggle method ───────────────────────────────────────────────────────
  const handleToggleMethod = (_, newMethods) => {
    setSelectedMethods(newMethods ?? [])
  }

  // ── Apply path + methods → điền vào predicates ──────────────────────────
  const handleApplyMapping = () => {
    if (!selectedMapping) return
    const predicateJson = buildPredicateJson(selectedMapping.path, selectedMethods)
    setForm(f => ({ ...f, predicates: predicateJson }))
    setJsonError(j => ({ ...j, predicates: '' }))
    if (errors.predicates) setErrors(e => ({ ...e, predicates: '' }))
  }

  // ── Generic field setter ─────────────────────────────────────────────────
  const set = (field) => (e) => {
    const val = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm(f => ({ ...f, [field]: val }))
    if (errors[field]) setErrors(e => ({ ...e, [field]: '' }))
    if (field === 'predicates' || field === 'filters') {
      try {
        JSON.parse(val)
        setJsonError(j => ({ ...j, [field]: '' }))
      } catch {
        setJsonError(j => ({ ...j, [field]: 'JSON không hợp lệ' }))
      }
    }
    if (field === 'uri') setSelectedService(null)
  }

  // ── Validate + save ──────────────────────────────────────────────────────
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

  // ─── Render ───────────────────────────────────────────────────────────────

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
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace" } } }}
            />
          </Grid>

          {/* ── Order ── */}
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
              control={<Switch checked={form.enabled} onChange={set('enabled')} color="primary" />}
              label={<Typography variant="body2" fontWeight={500}>{form.enabled ? 'Đang bật' : 'Đang tắt'}</Typography>}
            />
          </Grid>

          {/* ════════════════════════════════════════════════
              EUREKA SECTION
          ════════════════════════════════════════════════ */}
          <Grid size={12}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, borderColor: '#e2e8f0', bgcolor: '#f8fafc' }}>
              <Box sx={{ mb: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <CloudIcon sx={{ fontSize: 16, color: '#3b82f6' }} />
                <Typography variant="subtitle2" color="primary.main">Chọn từ Eureka</Typography>
                <Tooltip title="Làm mới danh sách service">
                  <span>
                    <IconButton size="small" onClick={fetchEurekaServices} disabled={eurekaLoading} sx={{ ml: 0.5 }}>
                      {eurekaLoading ? <CircularProgress size={14} /> : <RefreshIcon sx={{ fontSize: 16 }} />}
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
                      setMappings([])
                      setSelectedMapping(null)
                      setSelectedMethods([])
                    }}
                    sx={{ ml: 'auto', fontSize: '0.72rem' }}
                  />
                )}
              </Box>

              {/* ── 1. Service picker ── */}
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
                        <Typography variant="body2" fontWeight={600} sx={{ lineHeight: 1.4 }}>{svc.serviceId}</Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "'JetBrains Mono', monospace" }}>{url}</Typography>
                      </Box>
                    </Box>
                  )
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    placeholder="Tìm theo tên service..."
                    size="small"
                    label="Service"
                    error={Boolean(eurekaError)}
                    helperText={eurekaError || 'Chọn service để tự điền URI và load danh sách endpoints'}
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

              {/* ── 2. Mapping picker — chỉ hiện khi đã chọn service ── */}
              {selectedService && (
                <Box sx={{ mt: 2 }}>
                  <Box sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                    <RouteIcon sx={{ fontSize: 16, color: '#64748b' }} />
                    <Typography variant="subtitle2">Chọn path từ mappings</Typography>
                    {mappingsLoading && <CircularProgress size={12} sx={{ ml: 0.5 }} />}
                  </Box>

                  {mappingsError ? (
                    <Alert severity="warning" sx={{ borderRadius: 1.5, py: 0.5, fontSize: '0.8rem' }}>
                      {mappingsError}
                    </Alert>
                  ) : (
                    <>
                      <Autocomplete
                        options={mappings}
                        value={selectedMapping}
                        onChange={handleSelectMapping}
                        loading={mappingsLoading}
                        getOptionLabel={(m) => m.path}
                        isOptionEqualToValue={(a, b) => a.path === b.path}
                        noOptionsText={mappingsLoading ? 'Đang tải...' : 'Không có endpoint nào'}
                        groupBy={(m) => {
                          // Group theo prefix segment đầu: /api/auth/... → /api/auth
                          const parts = m.path.split('/').filter(Boolean)
                          return parts.length >= 2 ? `/${parts[0]}/${parts[1]}` : `/${parts[0] ?? ''}`
                        }}
                        renderOption={(props, m) => (
                          <Box component="li" {...props} key={m.path}
                            sx={{ display: 'flex', alignItems: 'center', gap: 1, py: '6px !important' }}>
                            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', minWidth: 120 }}>
                              {(m.methods.length > 0 ? m.methods : ['ANY']).map(met => (
                                <MethodChip key={met} method={met} />
                              ))}
                            </Box>
                            <Typography variant="body2" sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem' }}>
                              {m.path}
                            </Typography>
                          </Box>
                        )}
                        renderInput={(params) => (
                          <TextField
                            {...params}
                            placeholder="Tìm path, ví dụ: /api/auth/login..."
                            size="small"
                            label="Endpoint"
                            slotProps={{
                              input: {
                                ...params.InputProps,
                                endAdornment: (
                                  <>
                                    {mappingsLoading ? <CircularProgress size={14} /> : null}
                                    {params.InputProps.endAdornment}
                                  </>
                                ),
                              }
                            }}
                          />
                        )}
                      />

                      {/* ── 3. Method toggle + Apply button ── */}
                      {selectedMapping && (
                        <Box sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
                          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 48 }}>
                            Methods:
                          </Typography>
                          <ToggleButtonGroup
                            value={selectedMethods}
                            onChange={handleToggleMethod}
                            size="small"
                            sx={{ flexWrap: 'wrap', gap: 0.5, '& .MuiToggleButtonGroup-grouped': { border: '1px solid #e2e8f0 !important', borderRadius: '6px !important', m: 0 } }}
                          >
                            {ALL_METHODS.map(m => {
                              const style = METHOD_COLORS[m]
                              const isSelected = selectedMethods.includes(m)
                              return (
                                <ToggleButton
                                  key={m}
                                  value={m}
                                  sx={{
                                    px: 1.2, py: 0.3,
                                    fontSize: '0.68rem',
                                    fontFamily: "'JetBrains Mono', monospace",
                                    fontWeight: 700,
                                    lineHeight: 1.6,
                                    color: isSelected ? style.color : '#94a3b8',
                                    bgcolor: isSelected ? style.bg : 'transparent',
                                    borderColor: isSelected ? `${style.border} !important` : undefined,
                                    '&:hover': { bgcolor: style.bg, opacity: 0.85 },
                                  }}
                                >
                                  {m}
                                </ToggleButton>
                              )
                            })}
                          </ToggleButtonGroup>
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<AddIcon sx={{ fontSize: 14 }} />}
                            onClick={handleApplyMapping}
                            sx={{ ml: 'auto', fontSize: '0.75rem', py: 0.5, px: 1.5, borderRadius: 1.5 }}
                          >
                            Áp dụng vào Predicates
                          </Button>
                        </Box>
                      )}
                    </>
                  )}
                </Box>
              )}
            </Paper>
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
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem' } } }}
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
              slotProps={{ htmlInput: { style: { fontFamily: "'JetBrains Mono', monospace", fontSize: '0.8rem' } } }}
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
        <Button onClick={onClose} disabled={saving} color="inherit">Hủy</Button>
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
