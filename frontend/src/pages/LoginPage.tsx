import { useState, type FormEvent } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/AuthProvider'
import { ApiRequestError } from '@/api/client'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const from = (location.state as { from?: string } | undefined)?.from ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login({ username: username.trim(), password })
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.code === 'TOO_MANY_ATTEMPTS' ? 'Too many failed attempts. Try again later.' : 'Invalid credentials')
      } else {
        setError('Unexpected error')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-ink-950 px-4">
      <section className="w-full max-w-md">
        <header className="mb-8 text-center">
          <h1 className="font-mono text-sm font-semibold tracking-widest text-ink-100">SENTINELFLOW</h1>
          <p className="mt-2 text-ink-400">Risk Investigation Workspace</p>
        </header>

        <form onSubmit={handleSubmit} className="panel" noValidate>
          <div className="p-4">
            <div className="mb-4">
              <label htmlFor="username" className="block text-xs font-mono text-ink-400 mb-1">
                Username
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded border border-ink-700 bg-ink-900 px-3 py-2 text-ink-100 placeholder-ink-500 focus:outline-none focus:ring-2 focus:ring-ink-500"
                required
                disabled={loading}
              />
            </div>

            <div className="mb-4">
              <label htmlFor="password" className="block text-xs font-mono text-ink-400 mb-1">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded border border-ink-700 bg-ink-900 px-3 py-2 text-ink-100 placeholder-ink-500 focus:outline-none focus:ring-2 focus:ring-ink-500"
                required
                disabled={loading}
              />
            </div>

            {error && (
              <div className="mb-4 p-3 rounded border border-ink-700 bg-ink-900 text-ink-300 text-sm" role="alert">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded bg-ink-700 px-4 py-2.5 font-mono text-sm text-ink-100 hover:bg-ink-600 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-ink-500"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </div>
        </form>

        <p className="mt-6 text-center text-xs text-ink-500">
          Demo users: analyst/analyst-demo · investigator/investigator-demo · operator/operator-demo · admin/admin-demo
        </p>
      </section>
    </div>
  )
}