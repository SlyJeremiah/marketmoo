import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { getToken, login as apiLogin, setToken, setUnauthorizedHandler } from './api'

const Ctx = createContext(null)
export const useAuth = () => useContext(Ctx)

export function AuthProvider({ children }) {
  const [token, setTok] = useState(getToken())
  const [profile, setProfile] = useState(() => {
    try { return JSON.parse(sessionStorage.getItem('marketmoo_manager_profile') || 'null') } catch { return null }
  })

  const logout = useCallback(() => {
    setToken(null); sessionStorage.removeItem('marketmoo_manager_profile'); setTok(null); setProfile(null)
  }, [])

  useEffect(() => { setUnauthorizedHandler(logout) }, [logout])

  const login = useCallback(async (username, password) => {
    const r = await apiLogin(username, password)
    setToken(r.token); sessionStorage.setItem('marketmoo_manager_profile', JSON.stringify(r.profile))
    setTok(r.token); setProfile(r.profile)
  }, [])

  const value = useMemo(() => ({ token, profile, login, logout }), [token, profile, login, logout])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
