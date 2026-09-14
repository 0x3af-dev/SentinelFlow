import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { transactionsApi } from '@/api/transactions'
import { ApiRequestError } from '@/api/client'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

export function LookupPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [state, setState] = useState<{ loading: boolean; error: unknown; decided: boolean }>({
    loading: false,
    error: undefined,
    decided: false,
  })
  const [result, setResult] = useState<{ reference: string; decided: boolean } | null>(null)

  if (state.decided && result) {
    return <Navigate to={`/transactions/${encodeURIComponent(result.reference)}`} replace />
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const reference = query.trim()
    if (!reference) return
    setSubmitted(reference)
    setResult(null)
    setState({ loading: true, error: undefined, decided: false })
    try {
      const overview = await transactionsApi.overview(reference)
      setResult({ reference, decided: overview.decided })
      setState({ loading: false, error: undefined, decided: overview.decided })
    } catch (error) {
      setState({ loading: false, error, decided: false })
    }
  }

  const notFound = state.error instanceof ApiRequestError && state.error.status === 404

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="section-label mb-2">Transaction lookup</h1>
      <p className="mt-0 mb-6 text-[13px] text-ink-400">
        Enter a transaction reference to open its decision record and investigation context.
      </p>

      <form onSubmit={submit} role="search" className="flex items-stretch gap-2">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="e.g. txn-demo-001"
          spellCheck={false}
          autoCapitalize="none"
          className="min-w-0 flex-1 border border-ink-700 bg-ink-900 px-3 py-2 font-mono text-sm text-ink-100 placeholder:text-ink-500 focus:border-sky-500"
          aria-label="Transaction reference"
        />
        <button
          type="submit"
          disabled={state.loading || !query.trim()}
          className="border border-ink-600 bg-ink-800 px-5 font-mono text-sm text-ink-100 hover:bg-ink-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {state.loading ? 'Checking…' : 'Search'}
        </button>
      </form>

      <div className="mt-6">
        {state.loading && <Spinner label={`Looking up ${submitted}`} />}

        {notFound && (
          <ErrorPanel
            error={state.error}
          />
        )}

        {state.error && !notFound ? <ErrorPanel error={state.error} /> : null}

        {!state.loading && !state.error && result && !result.decided && (
          <div className="border border-ink-700 bg-ink-900 px-4 py-4">
            <p className="mt-0 font-mono text-sm text-ink-200">
              Transaction <span className="technical text-sky-400">{result.reference}</span> exists but has not been
              decided yet.
            </p>
            <button
              type="button"
              onClick={() => navigate(`/transactions/${encodeURIComponent(result.reference)}`)}
              className="mt-3 border border-ink-600 px-3 py-1.5 font-mono text-xs text-ink-100 hover:bg-ink-800"
            >
              Open workspace
            </button>
          </div>
        )}
      </div>
    </div>
  )
}