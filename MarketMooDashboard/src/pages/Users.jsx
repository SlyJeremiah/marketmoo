import { useState } from 'react'
import { api } from '../api'
import { Badge, Card, Loading, Message, Page, fmtDay, useAction, useApi } from '../ui.jsx'

const ROLES = ['farmer', 'buyer', 'vet', 'admin']
const DISTRICTS = [['mhondoro-ngezi', 'Mhondoro-Ngezi'], ['gwanda', 'Gwanda'], ['beitbridge', 'Beitbridge'], ['other', 'Other']]

// Shown once, right after create or reset. The server keeps only a hash, so it cannot be shown again.
function TempPassword({ info, onClose }) {
  const [copied, setCopied] = useState(false)
  if (!info) return null
  const copy = async () => {
    try { await navigator.clipboard.writeText(info.password); setCopied(true) } catch { /* user can copy by hand */ }
  }
  return (
    <Card title={`Temporary password for ${info.phone}`}>
      <p style={{ fontSize: '1.4rem', letterSpacing: '.06em', fontFamily: 'monospace', margin: '4px 0 10px' }} data-testid="temp-password">{info.password}</p>
      <p className="muted">Give this to the user now. It is not stored and cannot be shown again. They sign in on the app with phone number and password, and should change it under Account.</p>
      <div className="inline" style={{ marginBottom: 0 }}>
        <button className="btn" type="button" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
        <button className="btn ghost" type="button" onClick={onClose}>I have noted it</button>
      </div>
    </Card>
  )
}

function CreateUser({ onCreated, onShowPassword }) {
  const empty = { phone: '', role: 'farmer', district: 'other', verified: false, password: '' }
  const [form, setForm] = useState(empty)
  const { busy, message, run } = useAction()
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const submit = (e) => {
    e.preventDefault()
    run(async () => {
      const body = { ...form, phone: form.phone.trim() }
      if (!body.password) delete body.password
      const r = await api('/v1/manager/users/create', { method: 'POST', body })
      setForm(empty)
      onShowPassword(r.temporary_password ? { phone: r.phone, password: r.temporary_password } : null)
      onCreated()
    }, 'User created.')
  }
  return (
    <Card title="Create a user">
      <form className="form" onSubmit={submit} style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', alignItems: 'end' }}>
        <label>Phone number<input required placeholder="0771234567" value={form.phone} onChange={set('phone')} /></label>
        <label>Role<select value={form.role} onChange={set('role')}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select></label>
        <label>District<select value={form.district} onChange={set('district')}>{DISTRICTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label>Password (optional, 8+ characters)<input type="text" autoComplete="off" placeholder="leave empty to generate" value={form.password} onChange={set('password')} /></label>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}><input type="checkbox" checked={form.verified} onChange={set('verified')} style={{ width: 'auto' }} />Verified</label>
        <button className="btn" disabled={busy}>Create user</button>
      </form>
      <Message message={message} />
    </Card>
  )
}

export default function Users() {
  const [q, setQ] = useState('')
  const [term, setTerm] = useState('')
  const [shown, setShown] = useState(null)
  const users = useApi(`/v1/manager/users${term ? `?q=${encodeURIComponent(term)}` : ''}`)
  const { busy, message, run } = useAction()
  const post = (path, body) => api(path, { method: 'POST', body })
  const reset = (u) => {
    if (!window.confirm(`Reset the password for ${u.phone}? Their current password and signed-in sessions stop working.`)) return
    run(async () => {
      const r = await post(`/v1/manager/users/${u.id}/reset-password`, {})
      setShown({ phone: u.phone, password: r.temporary_password })
      users.reload()
    })
  }
  return (
    <Page title="Users" subtitle="Phone numbers only. No national ID is collected. Users can sign in with a text code or with a password you set here.">
      <TempPassword info={shown} onClose={() => setShown(null)} />
      <CreateUser onCreated={users.reload} onShowPassword={setShown} />
      <form className="inline" onSubmit={(e) => { e.preventDefault(); setTerm(q.trim()) }}>
        <input placeholder="Search by phone" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="btn">Search</button>
      </form>
      <Message message={message} />
      <Loading state={users} />
      {users.data && (
        <div className="table-wrap"><table>
          <thead><tr><th>Phone</th><th>Role</th><th>District</th><th>Joined</th><th>Listings</th><th>Records</th><th>Verified</th><th>Password</th><th>Account</th></tr></thead>
          <tbody>
            {users.data.results.length === 0 && <tr><td colSpan="9" className="muted">No users.</td></tr>}
            {users.data.results.map((u) => (
              <tr key={u.id}>
                <td>{u.phone}{u.staff && <> <Badge tone="blue">staff</Badge></>}</td><td>{u.role}</td><td>{u.district}</td><td>{fmtDay(u.joined)}</td><td>{u.listings}</td><td>{u.records}</td>
                <td>{u.verified ? <Badge tone="green">verified</Badge> : <Badge>no</Badge>}{' '}
                  <button className="link" disabled={busy} onClick={() => run(async () => { await post(`/v1/manager/users/${u.id}/verify`, { verified: !u.verified }); users.reload() })}>{u.verified ? 'Remove' : 'Verify'}</button></td>
                <td>{u.has_password ? <Badge tone="green">set</Badge> : <Badge>text code only</Badge>}{' '}
                  {!u.staff && <button className="link" disabled={busy} onClick={() => reset(u)}>{u.has_password ? 'Reset' : 'Set'}</button>}</td>
                <td>{u.active ? <Badge tone="green">active</Badge> : <Badge tone="red">deactivated</Badge>}{' '}
                  {!u.staff && <button className="link" disabled={busy} onClick={() => run(async () => { await post(`/v1/manager/users/${u.id}/active`, { active: !u.active }); users.reload() })}>{u.active ? 'Deactivate' : 'Activate'}</button>}</td>
              </tr>
            ))}
          </tbody>
        </table></div>
      )}
    </Page>
  )
}
