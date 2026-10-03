import { useState } from 'react'
import { api } from '../api'
import { Badge, Loading, Message, Page, fmtDay, useAction, useApi } from '../ui.jsx'

export default function Users() {
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  const users = useApi(`/v1/manager/users${term ? `?q=${encodeURIComponent(term)}` : ''}`)
  const { busy, message, run } = useAction()
  return (
    <Page title="Users" subtitle="Phone numbers only. No national ID is collected.">
      <form className="inline" onSubmit={(e) => { e.preventDefault(); setTerm(q.trim()) }}>
        <input placeholder="Search by phone" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="btn">Search</button>
      </form>
      <Message message={message} />
      <Loading state={users} />
      {users.data && (
        <div className="table-wrap"><table>
          <thead><tr><th>Phone</th><th>Role</th><th>District</th><th>Joined</th><th>Listings</th><th>Records</th><th>Verified</th></tr></thead>
          <tbody>
            {users.data.results.length === 0 && <tr><td colSpan="7" className="muted">No users.</td></tr>}
            {users.data.results.map((u) => (
              <tr key={u.id}>
                <td>{u.phone}</td><td>{u.role}</td><td>{u.district}</td><td>{fmtDay(u.joined)}</td><td>{u.listings}</td><td>{u.records}</td>
                <td>{u.verified ? <Badge tone="green">verified</Badge> : <Badge>no</Badge>}{' '}
                  <button className="link" disabled={busy} onClick={() => run(async () => { await api(`/v1/manager/users/${u.id}/verify`, { method: 'POST', body: { verified: !u.verified } }); users.reload() })}>{u.verified ? 'Remove' : 'Verify'}</button></td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </Page>
  )
}
