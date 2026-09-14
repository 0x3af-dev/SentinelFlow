import type { AuthUser, Role } from '@/api/types'

const STORAGE_KEY = 'sentinelflow.auth'
export const AUTH_EXPIRED_EVENT = 'auth-expired'
export const AUTH_LOGIN_EVENT = 'auth-login'

export interface PersistedSession {
  token: string
  username: string
  role: Role
  expiresInSeconds: number
}

function isPersistedSession(raw: unknown): raw is PersistedSession {
  return (
    typeof raw === 'object' &&
    raw !== null &&
    typeof (raw as Record<string, unknown>).token === 'string' &&
    typeof (raw as Record<string, unknown>).username === 'string' &&
    typeof (raw as Record<string, unknown>).role === 'string'
  )
}

export function saveSession(session: PersistedSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  window.dispatchEvent(new Event(AUTH_LOGIN_EVENT))
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY)
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
}

export function loadSession(): PersistedSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isPersistedSession(parsed)) return null
    return parsed
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

export function getToken(): string | null {
  return loadSession()?.token ?? null
}

export function currentUser(): AuthUser | null {
  const session = loadSession()
  if (!session) return null
  return { username: session.username, role: session.role }
}

export function hasRole(role: Role): boolean {
  return currentUser()?.role === role
}
