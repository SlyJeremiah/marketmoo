import { useState } from 'react'
import { useAuth } from '../auth.jsx'
import { API_URL } from '../api'
import { Message, useAction } from '../ui.jsx'

export default function Login() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const { busy, message, run } = useAction()
  return (
    <div className="login">
      <form className="card login-card" onSubmit={(e) => { e.preventDefault(); run(() => login(username.trim(), password)) }}>
        <div className="brand big">Market<span>Moo</span><small>Manager</small></div>
        <p className="muted">Staff sign-in. Farmers use the phone app; this site is for moderators and project managers.</p>
        <label>Phone number (staff account)
          <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="+263..." autoComplete="username" required />
        </label>
        <label>Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
        <Message message={message} />
        <p className="muted small">Server: {API_URL}</p>
      </form>
    </div>
  )
}
