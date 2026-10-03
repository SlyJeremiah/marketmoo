import { useState } from 'react'
import { api } from '../api'
import { Card, Loading, Message, Page, Status, fmtDate, fmtDay, useAction, useApi } from '../ui.jsx'

const blank = { disease: '', species: '', district: '', province: '', centre_lat: '', centre_lon: '', control_radius_km: 20, surveillance_radius_km: 40, started_on: '', summary: '', source_url: '', location_quality: '' }

function NoticeForm({ initial, onSubmit, onCancel, busy, title, submitLabel }) {
  const [f, setF] = useState({ ...blank, ...initial })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const num = (k) => (e) => setF({ ...f, [k]: e.target.value === '' ? '' : Number(e.target.value) })
  return (
    <form className="card form" onSubmit={(e) => { e.preventDefault(); onSubmit(f) }}>
      <h3>{title}</h3>
      <p className="muted small">Public notices show only the district and a rounded area centre (about 11 km), never a farm. Confirm with the Department of Veterinary Services before publishing.</p>
      <div className="grid3">
        <label>Disease<input value={f.disease} onChange={set('disease')} required /></label>
        <label>Species<input value={f.species} onChange={set('species')} /></label>
        <label>District<input value={f.district} onChange={set('district')} required /></label>
        <label>Centre latitude<input type="number" step="any" value={f.centre_lat} onChange={num('centre_lat')} required /></label>
        <label>Centre longitude<input type="number" step="any" value={f.centre_lon} onChange={num('centre_lon')} required /></label>
        <label>Started on<input type="date" value={f.started_on} onChange={set('started_on')} required /></label>
        <label>Control radius (km)<input type="number" min="0" step="any" value={f.control_radius_km} onChange={num('control_radius_km')} required /></label>
        <label>Surveillance radius (km)<input type="number" min="0" step="any" value={f.surveillance_radius_km} onChange={num('surveillance_radius_km')} required /></label>
        <label>Province<input value={f.province} onChange={set('province')} /></label>
      </div>
      <label>Summary<input value={f.summary} onChange={set('summary')} maxLength={300} /></label>
      <label>Source link<input type="url" value={f.source_url} onChange={set('source_url')} placeholder="https://" /></label>
      <label>Location quality<input value={f.location_quality} onChange={set('location_quality')} placeholder="for example district-level" /></label>
      <div className="actions">
        <button className="btn" disabled={busy}>{submitLabel}</button>
        {onCancel && <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  )
}

export default function Outbreaks() {
  const reports = useApi('/v1/manager/reports')
  const notices = useApi('/v1/manager/outbreaks')
  const { busy, message, run } = useAction()
  const [publishing, setPublishing] = useState(null)
  const [creating, setCreating] = useState(false)
  const clean = (f) => { const o = { ...f }; Object.keys(o).forEach((k) => o[k] === '' && delete o[k]); return o }
  const setReport = (id, status) => run(async () => { await api(`/v1/manager/reports/${id}/status`, { method: 'POST', body: { status } }); reports.reload() })
  return (
    <Page title="Disease" subtitle="Farmer reports are private until a manager verifies them with the Department of Veterinary Services and publishes a notice."
      actions={<button className="btn" onClick={() => setCreating(true)}>New notice</button>}>
      <Message message={message} />
      {creating && (
        <NoticeForm title="New public notice" submitLabel="Publish notice" busy={busy} onCancel={() => setCreating(false)}
          onSubmit={(f) => run(async () => { await api('/v1/manager/outbreaks', { method: 'POST', body: clean(f) }); setCreating(false); notices.reload() }, 'Notice published.')} />
      )}
      {publishing && (
        <NoticeForm title={`Publish a notice from report (${publishing.species}, ${publishing.suspected || 'unspecified'})`} submitLabel="Confirm and publish" busy={busy}
          initial={{ species: publishing.species, centre_lat: publishing.lat, centre_lon: publishing.lon, started_on: publishing.created_at.slice(0, 10), disease: publishing.suspected }}
          onCancel={() => setPublishing(null)}
          onSubmit={(f) => run(async () => { await api(`/v1/manager/reports/${publishing.id}/publish`, { method: 'POST', body: clean(f) }); setPublishing(null); reports.reload(); notices.reload() }, 'Notice published and report marked confirmed.')} />
      )}

      <Card title="Reports from farmers">
        <Loading state={reports} />
        {reports.data && (
          <div className="table-wrap"><table>
            <thead><tr><th>When</th><th>Species and suspicion</th><th>Count</th><th>Position (private)</th><th>Reporter</th><th>Status</th><th /></tr></thead>
            <tbody>
              {reports.data.results.length === 0 && <tr><td colSpan="7" className="muted">No reports.</td></tr>}
              {reports.data.results.map((r) => (
                <tr key={r.id}>
                  <td className="small">{fmtDate(r.created_at)}</td>
                  <td><b>{r.species}</b> {r.suspected}<div className="muted small">{r.description}</div></td>
                  <td>{r.count}</td><td className="small">{r.lat.toFixed(3)}, {r.lon.toFixed(3)}</td><td>{r.reporter_phone}</td><td><Status value={r.status} /></td>
                  <td className="row-actions">
                    {r.status === 'new' && <button className="btn sm ghost" disabled={busy} onClick={() => setReport(r.id, 'reviewing')}>Start review</button>}
                    {(r.status === 'new' || r.status === 'reviewing') && <button className="btn sm" disabled={busy} onClick={() => setPublishing(r)}>Publish notice</button>}
                    {(r.status === 'new' || r.status === 'reviewing') && <button className="btn sm danger" disabled={busy} onClick={() => setReport(r.id, 'dismissed')}>Dismiss</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </Card>

      <Card title="Public notices">
        <Loading state={notices} />
        {notices.data && (
          <div className="table-wrap"><table>
            <thead><tr><th>Disease</th><th>District</th><th>Since</th><th>Zones</th><th>Source</th><th>Status</th><th /></tr></thead>
            <tbody>
              {notices.data.results.length === 0 && <tr><td colSpan="7" className="muted">No notices.</td></tr>}
              {notices.data.results.map((o) => (
                <tr key={o.id}>
                  <td><b>{o.disease}</b><div className="muted small">{o.summary}</div></td><td>{o.district}</td><td>{fmtDay(o.started_on)}</td>
                  <td className="small">control {o.control_radius_km} km, surveillance {o.surveillance_radius_km} km<div className="muted">{o.location_quality}</div></td>
                  <td className="small">{o.source_url ? <a href={o.source_url} target="_blank" rel="noreferrer">link</a> : '-'}</td>
                  <td><Status value={o.status} /></td>
                  <td>{o.status === 'verified' && <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api(`/v1/manager/outbreaks/${o.id}/close`, { method: 'POST', body: {} }); notices.reload() })}>Close</button>}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </Card>
    </Page>
  )
}
