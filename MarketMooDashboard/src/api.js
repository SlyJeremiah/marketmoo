// Small fetch wrapper for the MarketMoo manager API. The token lives in sessionStorage, so closing the tab signs out.
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8000').replace(/\/$/, '')
const KEY = 'marketmoo_manager_token'

export const getToken = () => sessionStorage.getItem(KEY)
export const setToken = (t) => (t ? sessionStorage.setItem(KEY, t) : sessionStorage.removeItem(KEY))

let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn }

export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status }
}

export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && getToken()) headers.Authorization = `Token ${getToken()}`
  let res
  try {
    res = await fetch(API_URL + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. It may be waking up (free hosting): wait a minute and try again.')
  }
  if (res.status === 401 || (res.status === 403 && auth)) { onUnauthorized(); throw new ApiError(res.status, 'Please sign in again.') }
  const text = await res.text()
  let data = {}
  try { data = text ? JSON.parse(text) : {} } catch { /* non-JSON error page */ }
  if (!res.ok) throw new ApiError(res.status, describeError(data) || `Request failed (${res.status})`)
  return data
}

// DRF returns either {detail: ...} or {field: [messages]}
export function describeError(data) {
  if (!data || typeof data !== 'object') return ''
  if (data.detail) return String(data.detail)
  const parts = Object.entries(data).map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join(' ') : v}`)
  return parts.join('; ')
}

export const login = (username, password) => api('/v1/auth/staff-login', { method: 'POST', body: { username, password }, auth: false })
