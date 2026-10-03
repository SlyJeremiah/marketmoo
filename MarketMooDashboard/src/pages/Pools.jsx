import { useState } from 'react'
import { api } from '../api'
import { Card, Loading, Message, Page, Status, fmtDay, useAction, useApi } from '../ui.jsx'

const species = ['cattle', 'goats', 'sheep', 'pigs', 'poultry']

export default function Pools() {
  const pools = useApi('/v1/manager/pools')
  const { busy, message, run } = useAction()
  const [f, setF] = useState({ species: 'cattle', title: '', target_qty: 50, deadline: '', buyer_note: '' })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  return (
    <Page title="Pools" subtitle="Groups of farmers who sell together to meet a buyer's volume. Commitments are checked by the server, so a pool that closed while a phone was offline is rejected cleanly.">
      <Message message={message} />
      <Card title="Create a pool">
        <form className="form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api('/v1/manager/pools', { method: 'POST', body: { ...f, target_qty: Number(f.target_qty) } }); setF({ ...f, title: '' }); pools.reload() }, 'Pool created.') }}>
          <div className="grid3">
            <label>Species<select value={f.species} onChange={set('species')}>{species.map((s) => <option key={s}>{s}</option>)}</select></label>
            <label>Title<input value={f.title} onChange={set('title')} required /></label>
            <label>Target quantity<input type="number" min="1" value={f.target_qty} onChange={set('target_qty')} required /></label>
            <label>Deadline<input type="date" value={f.deadline} onChange={set('deadline')} required /></label>
          </div>
          <label>Buyer note<input value={f.buyer_note} onChange={set('buyer_note')} maxLength={200} /></label>
          <button className="btn" disabled={busy}>Create pool</button>
        </form>
      </Card>
      <Loading state={pools} />
      {pools.data && (
        <div className="table-wrap"><table>
          <thead><tr><th>Pool</th><th>Progress</th><th>Deadline</th><th>Status</th><th /></tr></thead>
          <tbody>
            {pools.data.results.length === 0 && <tr><td colSpan="5" className="muted">No pools yet.</td></tr>}
            {pools.data.results.map((p) => (
              <tr key={p.id}>
                <td><b>{p.title}</b><div className="muted small">{p.species}</div></td>
                <td style={{ minWidth: 180 }}>{p.committed} / {p.target_qty} ({p.progress_pct}%)<div className="bar"><span style={{ width: `${p.progress_pct}%` }} /></div></td>
                <td>{fmtDay(p.deadline)}</td><td><Status value={p.status} /></td>
                <td className="row-actions">
                  {p.status !== 'open' && <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api(`/v1/manager/pools/${p.id}/status`, { method: 'POST', body: { status: 'open' } }); pools.reload() })}>Reopen</button>}
                  {p.status === 'open' && <button className="btn sm ghost" disabled={busy} onClick={() => run(async () => { await api(`/v1/manager/pools/${p.id}/status`, { method: 'POST', body: { status: 'lapsed' } }); pools.reload() })}>Close as lapsed</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </Page>
  )
}
