import { screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AppShell } from '@/components/layout/AppShell'
import { renderWithAuth } from '@/test/render'
import { clearSession } from '@/auth/session'

vi.mock('@/components/layout/SearchBar', () => ({
  SearchBar: () => <span data-testid="search-bar" />,
}))

function renderShell(opts?: { username?: string; role?: 'ANALYST' | 'INVESTIGATOR' | 'OPERATOR' | 'ADMIN' }) {
  clearSession()
  return renderWithAuth(<AppShell />, { route: '/', ...opts })
}

describe('AppShell auth', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clearSession()
  })

  it('shows login prompt when unauthenticated', () => {
    renderShell()
    expect(screen.queryByText('SENTINELFLOW')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /logout/i })).not.toBeInTheDocument()
  })

  it('shows username, role badge, and logout when authenticated', () => {
    renderShell({ username: 'analyst', role: 'ANALYST' })
    expect(screen.getByText('analyst')).toBeInTheDocument()
    expect(screen.getByText('ANALYST')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument()
  })

  it('shows Investigations link for ANALYST', () => {
    renderShell({ username: 'analyst', role: 'ANALYST' })
    expect(screen.getByText('Investigations')).toBeInTheDocument()
  })

  it('shows Investigations link for INVESTIGATOR', () => {
    renderShell({ username: 'inv', role: 'INVESTIGATOR' })
    expect(screen.getByText('Investigations')).toBeInTheDocument()
  })

  it('shows Investigations link for ADMIN', () => {
    renderShell({ username: 'admin', role: 'ADMIN' })
    expect(screen.getByText('Investigations')).toBeInTheDocument()
  })

  it('hides Investigations link for OPERATOR', () => {
    renderShell({ username: 'op', role: 'OPERATOR' })
    expect(screen.queryByText('Investigations')).not.toBeInTheDocument()
  })

  it('shows System Health for OPERATOR', () => {
    renderShell({ username: 'op', role: 'OPERATOR' })
    expect(screen.getByText('System Health')).toBeInTheDocument()
  })

  it('shows System Health for ADMIN', () => {
    renderShell({ username: 'admin', role: 'ADMIN' })
    expect(screen.getByText('System Health')).toBeInTheDocument()
  })

  it('hides System Health for ANALYST', () => {
    renderShell({ username: 'analyst', role: 'ANALYST' })
    expect(screen.queryByText('System Health')).not.toBeInTheDocument()
  })

  it('hides System Health for INVESTIGATOR', () => {
    renderShell({ username: 'inv', role: 'INVESTIGATOR' })
    expect(screen.queryByText('System Health')).not.toBeInTheDocument()
  })
})