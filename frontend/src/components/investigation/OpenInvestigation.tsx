import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { InvestigationMetadata, Priority } from '@/api/types'
import { investigationsApi } from '@/api/investigations'
import { formatTimestamp } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

interface OpenInvestigationProps {
  transactionReference: string
  existing: InvestigationMetadata[]
  onCreated: (created: InvestigationMetadata) => void
}

const OPEN_STATUSES = new Set(['OPEN', 'INVESTIGATING'])

/**
 * §61–62: creates an investigation; existing investigations are linked, never
 * silently duplicated.
 */
export function OpenInvestigation({ transactionReference, existing, onCreated }: OpenInvestigationProps) {
  const active = existing.filter((i) => OPEN_STATUSES.has(i.status))
  const previous = existing.filter((i) => !OPEN_STATUSES.has(i.status))
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [note, setNote] = useState('')
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<unknown>(undefined)
  const [created, setCreated] = useState<InvestigationMetadata | null>(null)

  const run = async () => {
    setRunning(true)
    setError(undefined)
    try {
      const trimmedNote = note.trim()
      const request = trimmedNote
        ? { transactionReference, priority, note: trimmedNote }
        : { transactionReference, priority }
      const metadata = await investigationsApi.create(request)
      setCreated(metadata)
      onCreated(metadata)
    } catch (err) {
      setError(err)
    } finally {
      setRunning(false)
    }
  }

  return (
    <Panel title="Investigation" testId="open-investigation">
      {active.length > 0 && (
        <div className="mb-3 space-y-1">
          {active.map((item) => (
            <div key={item.id} className="flex flex-wrap items-center gap-2 text-xs">
              <span className="technical text-ink-200">{item.investigationReference}</span>
              <span className="border border-ink-600 px-1.5 py-0 font-mono text-[10px] text-ink-300">
                {item.status}
              </span>
              <Link
                to={`/investigations/${encodeURIComponent(item.id)}`}
                className="ml-auto font-mono text-[11px] text-sky-300 hover:text-sky-200"
              >
                Open →
              </Link>
            </div>
          ))}
          {active.length > 0 && previous.length === 0 && (
            <p className="mt-1 mb-0 text-[11px] text-ink-500">
              An active investigation exists; a new one is not needed.
            </p>
          )}
          {previous.length > 0 && (
            <details className="mt-2 pt-2 text-[11px] text-ink-500">
              <summary className="cursor-pointer select-none">Resolved history ({previous.length})</summary>
              <ul className="m-0 mt-1 list-none space-y-0.5">
                {previous.map((item) => (
                  <li key={item.id} className="flex gap-2">
                    <Link to={`/investigations/${encodeURIComponent(item.id)}`} className="technical text-sky-400 hover:text-sky-300">
                      {item.investigationReference}
                    </Link>
                    <span className="text-ink-600">{item.resolution ?? ''}</span>
                    <span className="ml-auto technical">{formatTimestamp(item.resolvedAt ?? item.updatedAt)}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}

      {created ? (
        <div className="border border-sky-500/40 bg-sky-500/10 p-3">
          <p className="mt-0 mb-1 text-[13px] text-sky-200">
            Investigation <span className="technical">{created.investigationReference}</span> opened.
          </p>
          <Link
            to={`/investigations/${encodeURIComponent(created.id)}`}
            className="font-mono text-xs text-sky-300 underline-offset-2 hover:underline"
          >
            Open workspace →
          </Link>
        </div>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void run() }} className="space-y-3">
          <label className="block">
            <span className="section-label">Priority</span>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as Priority)}
              className="mt-1 border border-ink-700 bg-ink-900 px-2 py-1.5 font-mono text-sm text-ink-100 focus:border-sky-500"
            >
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
          </label>
          <label className="block">
            <span className="section-label">Opening note</span>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
              placeholder="Context for this investigation…"
              className="mt-1 w-full border border-ink-700 bg-ink-900 px-2 py-1.5 text-sm text-ink-100 focus:border-sky-500"
            />
          </label>
          <div className="flex items-center gap-3">
            <button
              type="submit"
              disabled={running}
              className="border border-sky-500/50 bg-sky-500/10 px-4 py-1.5 font-mono text-xs uppercase text-sky-300 hover:bg-sky-500/20 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {running ? 'Opening…' : 'Open investigation'}
            </button>
            {running && <Spinner />}
          </div>
          {error ? <ErrorPanel error={error} onRetry={run} /> : null}
        </form>
      )}
    </Panel>
  )
}