import { render, type RenderResult } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import type { ReactElement } from 'react'
import type { Role } from '@/api/types'
import { saveSession } from '@/auth/session'

export function renderWithRouter(
  ui: ReactElement,
  { route = '/' }: { route?: string } = {},
): RenderResult {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>)
}

export function renderWithAuth(
  ui: ReactElement,
  {
    route = '/',
    username,
    role,
  }: { route?: string; username?: string; role?: Role } = {},
): RenderResult {
  if (username && role) {
    saveSession({ token: 'test-token', username, role, expiresInSeconds: 3600 })
  }
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  )
}