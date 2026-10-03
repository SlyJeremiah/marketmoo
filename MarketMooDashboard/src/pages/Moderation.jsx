import { useState } from 'react'
import { api } from '../api'
import { Badge, Loading, Message, Page, Status, fmtDate, fmtMoney, useAction, useApi } from '../ui.jsx'

const FILTERS = ['pending', 'live', 'rejected', 'removed', 'sold', '']

export default function Moderation() {
  const [status, setStatus] = useState('pending')
  const list = useApi(`/v1/manager/listings${status ? `?status=${status}` : ''}`)
  const { busy, message, run } = useAction()
  const setListing = (id, s) => run(async () => { await api(`/v1/manager/listings/${id}/status`, { method: 'POST', body: { status: s } }); list.reload() })
  const verify = (phone, ownerId, v) => run(async () => { await api(`/v1/manager/users/${ownerId}/verify`, { method: 'POST', body: { verified: v } }); list.reload() })
  return (
    <Page title="Listings" subtitle="New sellers' listings stay hidden until a manager approves them. Approve only what looks genuine.">
      <div className="tabs">
        {FILTERS.map((f) => <button key={f || 'all'} className={status === f ? 'tab on' : 'tab'} onClick={() => setStatus(f)}>{f || 'all'}</button>)}
      </div>
      <Message message={message} />
      <Loading state={list} />
      {list.data && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Animal</th><th>Price</th><th>Where</th><th>Seller</th><th>Status</th><th>Updated</th><th /></tr></thead>
            <tbody>
              {list.data.results.length === 0 && <tr><td colSpan="7" className="muted">Nothing here.</td></tr>}
              {list.data.results.map((l) => (
                <tr key={l.id}>
                  <td><b>{l.species}</b> {l.breed} x{l.qty}<div className="muted small">{l.sex}, {l.age_months} months{l.photo_key ? ', has photo' : ''}</div></td>
                  <td>{fmtMoney(l.price_usd)}</td>
                  <td>{l.ward || '-'}<div className="muted small">{l.public_lat.toFixed(2)}, {l.public_lon.toFixed(2)}</div></td>
                  <td>{l.owner_phone}{l.owner_verified && <> <Badge tone="green">verified</Badge></>}
                    <div><button className="link" disabled={busy} onClick={() => verify(l.owner_phone, l.owner_id ?? l.owner, !l.owner_verified)}>{l.owner_verified ? 'Remove badge' : 'Verify seller'}</button></div></td>
                  <td><Status value={l.status} /></td>
                  <td className="small">{fmtDate(l.updated_at)}</td>
                  <td className="row-actions">
                    {l.status !== 'live' && <button className="btn sm" disabled={busy} onClick={() => setListing(l.id, 'live')}>Approve</button>}
                    {l.status === 'pending' && <button className="btn sm danger" disabled={busy} onClick={() => setListing(l.id, 'rejected')}>Reject</button>}
                    {l.status === 'live' && <button className="btn sm ghost" disabled={busy} onClick={() => setListing(l.id, 'sold')}>Mark sold</button>}
                    {l.status === 'live' && <button className="btn sm danger" disabled={busy} onClick={() => setListing(l.id, 'removed')}>Remove</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Page>
  )
}
