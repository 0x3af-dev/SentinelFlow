import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { InvestigationMetadata } from '@/api/types'
import { investigationsApi } from '@/api/investigations'
import { formatTimestamp } from '@/lib/format'
import { priorityTone } from '@/lib/severity'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { EmptyState } from '@/components/ui/EmptyState'

const STATUS_TONE: Record<string, string> = {
  OPEN: 'border-sky-500/50 bg-sky-500/10 text-sky-300',
  INVESTIGATING: 'border-amber-500/50 bg-amber-500/10 text-amber-300',
  RESOLVED: 'border-emerald-500/50 bg-emerald-500/10 text-emerald-300',
}

export function InvestigationsPage() {
  const [items, setItems] = useState<InvestigationMetadata[] | null>(null)
  const [error, setError] = useState<unknown>(undefined)
  const [filter, setFilter] = useState('')

  const load = useCallback(async () => {
    try {
      setItems(await investigationsApi.list())
    } catch (err) {
      setError(err)
    }
  }, [])

  const retry = useCallback(() => {
    setError(undefined)
    void load()
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch-on-mount; setState only after await
    void load()
  }, [load])

  const query = filter.trim().toLowerCase()
  const visible = (items ?? []).filter((item) => {
    if (!query) return true
    return (
      item.investigationReference.toLowerCase().includes(query) ||
      item.transactionReference.toLowerCase().includes(query)
    )
  })

  return (
    <div className="px-6 py-8">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-5">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="m-0 text-base font-semibold tracking-widest text-ink-100">INVESTIGATIONS</h1>
          {items && <span className="technical text-xs text-ink-500">{items.length} total</span>}
          <input
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter by reference…"
            className="ml-auto w-64 border border-ink-700 bg-ink-900 px-2 py-1.5 font-mono text-sm text-ink-100 placeholder:text-ink-500 focus:border-sky-500"
            aria-label="Filter investigations"
          />
        </div>

        {error ? <ErrorPanel error={error} onRetry={retry} /> : null}

        {!error && !items && <Spinner label="Loading investigations" />}

        {!error && items !== null && visible.length === 0 && (
          <EmptyState
            message={query ? 'No investigations match the filter.' : 'No investigations recorded yet.'}
            hint="Open an investigation from a transaction workspace."
          />
        )}

        {!error && visible.length > 0 && (
          <table className="w-full border-collapse text-xs">
            <thead>
              <tr className="border-b border-ink-700 text-left">
                <th className="section-label py-2 pr-4 font-normal">Reference</th>
                <th className="section-label py-2 pr-4 font-normal">Transaction</th>
                <th className="section-label py-2 pr-4 font-normal">Status</th>
                <th className="section-label py-2 pr-4 font-normal">Priority</th>
                <th className="section-label py-2 pr-4 font-normal">Events</th>
                <th className="section-label py-2 pr-4 font-normal">Opened</th>
                <th className="section-label py-2 font-normal" />
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-800">
              {visible.map((item) => {
                const statusTone = STATUS_TONE[item.status] ?? 'border-ink-600 text-ink-300'
                return (
                  <tr key={item.id} className="hover:bg-ink-900">
                    <td className="py-2 pr-4">
                      <span className="technical text-ink-100">{item.investigationReference}</span>
                    </td>
                    <td className="py-2 pr-4">
                      <Link
                        to={`/transactions/${encodeURIComponent(item.transactionReference)}`}
                        className="technical text-sky-400 hover:text-sky-300"
                      >
                        {item.transactionReference}
                      </Link>
                    </td>
                    <td className="py-2 pr-4">
                      <span className={`border px-1.5 py-0.5 font-mono text-[10px] ${statusTone}`}>{item.status}</span>
                    </td>
                    <td className="py-2 pr-4">
                      <span className={`border px-1.5 py-0.5 font-mono text-[10px] ${priorityTone[item.priority] ?? 'border-ink-600 text-ink-300'}`}>
                        {item.priority}
                      </span>
                    </td>
                    <td className="py-2 pr-4 technical text-ink-400">{item.eventCount}</td>
                    <td className="py-2 pr-4 technical text-ink-400">{formatTimestamp(item.openedAt)}</td>
                    <td className="py-2 pr-4 text-right">
                      <Link
                        to={`/investigations/${encodeURIComponent(item.id)}`}
                        className="font-mono text-[11px] text-sky-300 hover:text-sky-200"
                      >
                        Open →
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}