import { Suspense, lazy } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Login from './pages/Login.jsx'
import Moderation from './pages/Moderation.jsx'
import Outbreaks from './pages/Outbreaks.jsx'
import Pools from './pages/Pools.jsx'
import Users from './pages/Users.jsx'
import Packs from './pages/Packs.jsx'

// charts and the map are the heavy parts of the bundle, so load them on demand
const Overview = lazy(() => import('./pages/Overview.jsx'))
const MapPage = lazy(() => import('./pages/MapPage.jsx'))

const nav = [
  ['/', 'Overview'], ['/moderation', 'Listings'], ['/outbreaks', 'Disease'], ['/pools', 'Pools'],
  ['/map', 'Map'], ['/users', 'Users'], ['/packs', 'Data packs'],
]

export default function App() {
  const { token, profile, logout } = useAuth()
  if (!token) return <Login />
  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">Market<span>Moo</span><small>Manager</small></div>
        <nav>
          {nav.map(([to, label]) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>{label}</NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <div>{profile?.phone}</div>
          <button className="btn ghost" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main">
        <Suspense fallback={<p className="muted">Loading...</p>}>
        <Routes>
          <Route path="/" element={<Overview />} />
          <Route path="/moderation" element={<Moderation />} />
          <Route path="/outbreaks" element={<Outbreaks />} />
          <Route path="/pools" element={<Pools />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/users" element={<Users />} />
          <Route path="/packs" element={<Packs />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </main>
    </div>
  )
}
