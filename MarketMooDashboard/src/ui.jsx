import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

/** Loads a GET endpoint and exposes data, error, loading and reload. */
export function useApi(path, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try { setState({ data: await api(path), error: null, loading: false }) }
    catch (e) { setState({ data: null, error: e.message, loading: false }) }
  }, [path])
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [load, ...deps])
  return { ...state, reload: load }
}

export function Page({ title, subtitle, actions, children }) {
  return (
    <section>
      <header className="page-head">
        <div><h1>{title}</h1>{subtitle && <p className="muted">{subtitle}</p>}</div>
        <div className="actions">{actions}</div>
      </header>
      {children}
    </section>
  )
}

export const Card = ({ title, children, className = '' }) => (
  <div className={`card ${className}`}>{title && <h3>{title}</h3>}{children}</div>
)

export const Stat = ({ label, value, hint, tone }) => (
  <div className={`stat ${tone || ''}`}><div className="stat-value">{value ?? '-'}</div><div className="stat-label">{label}</div>{hint && <div className="muted small">{hint}</div>}</div>
)

export const Badge = ({ children, tone = 'grey' }) => <span className={`badge ${tone}`}>{children}</span>

export const statusTone = { pending: 'orange', live: 'green', sold: 'blue', removed: 'grey', rejected: 'red', new: 'orange', reviewing: 'blue', confirmed: 'green', dismissed: 'grey', open: 'green', met: 'blue', lapsed: 'grey', verified: 'green', closed: 'grey' }

export function Status({ value }) { return <Badge tone={statusTone[value] || 'grey'}>{value}</Badge> }

export function Loading({ state }) {
  if (state.loading && !state.data) return <p className="muted">Loading...</p>
  if (state.error) return <p className="error">{state.error} <button className="link" onClick={state.reload}>Retry</button></p>
  return null
}

export function useAction() {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)
  const run = async (fn, okText) => {
    setBusy(true); setMessage(null)
    try { const r = await fn(); if (okText) setMessage({ ok: true, text: okText }); return r }
    catch (e) { setMessage({ ok: false, text: e.message }); return undefined }
    finally { setBusy(false) }
  }
  return { busy, message, run }
}

export const Message = ({ message }) => (message ? <p className={message.ok ? 'ok' : 'error'}>{message.text}</p> : null)

export const fmtDate = (s) => (s ? new Date(s).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '')
export const fmtDay = (s) => (s ? String(s).slice(0, 10) : '')
export const fmtMoney = (n) => `$${Number(n).toLocaleString('en-US', { maximumFractionDigits: 0 })}`
export const fmtBytes = (n) => (n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.round(n / 1024)} KB`)
