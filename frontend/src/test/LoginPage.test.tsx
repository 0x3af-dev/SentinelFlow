import { screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LoginPage } from '@/pages/LoginPage'
import * as authApi from '@/api/auth'
import { clearSession } from '@/auth/session'
import { renderWithAuth } from '@/test/render'
import { ApiRequestError } from '@/api/client'

vi.mock('@/api/auth')

function renderPage() {
  clearSession()
  return renderWithAuth(<LoginPage />, { route: '/login' })
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clearSession()
  })

  it('renders username and password fields', () => {
    renderPage()
    expect(screen.getByLabelText('Username')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows error on invalid credentials', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new ApiRequestError(401, 'AUTHENTICATION_REQUIRED', 'Valid credentials are required', {}),
    )

    renderPage()
    ;(screen.getByLabelText('Username') as HTMLInputElement).value = 'analyst'
    ;(screen.getByLabelText('Password') as HTMLInputElement).value = 'wrong'
    screen.getByRole('button', { name: /sign in/i }).click()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid credentials')
    })
  })

  it('shows error on rate limit', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new ApiRequestError(429, 'TOO_MANY_ATTEMPTS', 'Too many failed attempts. Try again later.', {}),
    )

    renderPage()
    ;(screen.getByLabelText('Username') as HTMLInputElement).value = 'analyst'
    ;(screen.getByLabelText('Password') as HTMLInputElement).value = 'analyst-demo'
    screen.getByRole('button', { name: /sign in/i }).click()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Too many failed attempts. Try again later.')
    })
  })

  it('disables inputs while loading', async () => {
    let resolve: (v: any) => void
    vi.mocked(authApi.authApi.login).mockImplementation(
      () => new Promise((r) => { resolve = r })
    )

    renderPage()
    ;(screen.getByLabelText('Username') as HTMLInputElement).value = 'analyst'
    ;(screen.getByLabelText('Password') as HTMLInputElement).value = 'analyst-demo'
    const btn = screen.getByRole('button', { name: /sign in/i })
    btn.click()

    await waitFor(() => {
      expect(btn).toHaveTextContent('Signing in…')
      expect(screen.getByLabelText('Username')).toBeDisabled()
      expect(screen.getByLabelText('Password')).toBeDisabled()
    })

    resolve!({ token: 't', username: 'analyst', role: 'ANALYST', expiresInSeconds: 3600 })
  })
})