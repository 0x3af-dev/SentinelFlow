import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { SearchBar } from './SearchBar'

export function AppShell() {
  const navigate = useNavigate()
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
          <NavLink
            to="/investigations"
            className={({ isActive }) =>
              `px-2 py-1.5 font-mono text-[13px] ${isActive ? 'bg-ink-800 text-ink-100' : 'text-ink-400 hover:text-ink-200'}`
            }
          >
            Investigations
          </NavLink>
          <NavLink
            to="/system-health"
            className={({ isActive }) =>
              `px-2 py-1.5 font-mono text-[13px] ${isActive ? 'bg-ink-800 text-ink-100' : 'text-ink-400 hover:text-ink-200'}`
            }
          >
            System Health
          </NavLink>
        </nav>

        <main className="min-w-0 flex-1 overflow-y-auto bg-ink-950">
          <Outlet />
        </main>
      </div>
    </div>
  )
}