import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { SearchBar } from './SearchBar'
import { useAuth } from '@/auth/AuthProvider'
import type { Role } from '@/api/types'

function RoleBadge({ role }: { role: Role }) {
  const colors: Record<Role, string> = {
    ADMIN: 'bg-ink-700 text-ink-100',
    ANALYST: 'bg-emerald-900/30 text-emerald-300 border border-emerald-700',
    INVESTIGATOR: 'bg-blue-900/30 text-blue-300 border border-blue-700',
    OPERATOR: 'bg-amber-900/30 text-amber-300 border border-amber-700',
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 font-mono text-[11px] ${colors[role]}`}>
      {role}
    </span>
  )
}

export function AppShell() {
  const navigate = useNavigate()
  const { user, logout, hasRole } = useAuth()

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-ink-700 bg-ink-900 px-4 py-2.5">
        <button
          type="button"
          onClick={() => navigate('/')}
          className="flex items-baseline gap-2 bg-transparent"
          aria-label="SentinelFlow home"
        >
          <span className="font-mono text-sm font-semibold tracking-widest text-ink-100">SENTINELFLOW</span>
          <span className="section-label hidden sm:inline">Risk Investigation Workspace</span>
        </button>
        <SearchBar />
        {user && (
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm text-ink-200">{user.username}</span>
            <RoleBadge role={user.role} />
            <button
              type="button"
              onClick={logout}
              className="rounded border border-ink-700 bg-ink-800 px-3 py-1.5 font-mono text-[12px] text-ink-300 hover:bg-ink-700 hover:text-ink-100 focus:outline-none focus:ring-2 focus:ring-ink-500"
            >
              Logout
            </button>
          </div>
        )}
      </header>

      <div className="flex min-h-0 flex-1">
        <nav className="flex w-44 shrink-0 flex-col gap-0.5 border-r border-ink-700 bg-ink-950 p-3" aria-label="Primary">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              `px-2 py-1.5 font-mono text-[13px] ${isActive ? 'bg-ink-800 text-ink-100' : 'text-ink-400 hover:text-ink-200'}`
            }
          >
            Transactions
          </NavLink>
          {hasRole('ANALYST', 'INVESTIGATOR', 'ADMIN') && (
            <NavLink
              to="/investigations"
              className={({ isActive }) =>
                `px-2 py-1.5 font-mono text-[13px] ${isActive ? 'bg-ink-800 text-ink-100' : 'text-ink-400 hover:text-ink-200'}`
              }
            >
              Investigations
            </NavLink>
          )}
          {hasRole('OPERATOR', 'ADMIN') && (
            <NavLink
              to="/system-health"
              className={({ isActive }) =>
                `px-2 py-1.5 font-mono text-[13px] ${isActive ? 'bg-ink-800 text-ink-100' : 'text-ink-400 hover:text-ink-200'}`
              }
            >
              System Health
            </NavLink>
          )}
        </nav>

        <main className="min-w-0 flex-1 overflow-y-auto bg-ink-950">
          <Outlet />
        </main>
      </div>
    </div>
  )
}