import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { LoginRequest, Role } from '@/api/types'
import { authApi } from '@/api/auth'
import { clearSession, currentUser, saveSession } from './session'

interface AuthContextValue {
  user: ReturnType<typeof currentUser>
  login: (credentials: LoginRequest) => Promise<void>
  logout: () => void
  hasRole: (...roles: Role[]) => boolean
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<ReturnType<typeof currentUser>>(() => currentUser())

  useEffect(() => {
    const refresh = () => setUser(currentUser())
    window.addEventListener('auth-login', refresh)
    window.addEventListener('auth-expired', refresh)
    return () => {
      window.removeEventListener('auth-login', refresh)
      window.removeEventListener('auth-expired', refresh)
    }
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await authApi.login(credentials)
    saveSession(response)
    setUser({ username: response.username, role: response.role })
  }, [])

  const logout = useCallback(() => {
    void authApi.logout().catch(() => undefined)
    clearSession()
    setUser(null)
  }, [])

  const hasRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  )

  const value = useMemo<AuthContextValue>(
    () => ({ user, login, logout, hasRole, isAuthenticated: user !== null }),
    [user, login, logout, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}