import { useState } from 'react'
import { investigationsApi } from '@/api/investigations'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

interface AddEventNoteProps {
  investigationId: string
  onAdded: () => void
}

/** Appends a NOTE_ADDED event with the note payload to the investigation audit. */
export function AddEventNote({ investigationId, onAdded }: AddEventNoteProps) {
  const [note, setNote] = useState('')
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<unknown>(undefined)

  const run = async () => {
    if (!note.trim()) return
    setRunning(true)
    setError(undefined)
    try {
      await investigationsApi.addEvent(investigationId, {
        eventType: 'NOTE_ADDED',
        actorType: 'ANALYST',
        payload: { note: note.trim() },
      })
      setNote('')
      onAdded()
    } catch (err) {
      setError(err)
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="space-y-2">
      <label className="block">
        <span className="section-label">Add note to the audit trail</span>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={2}
          placeholder="Findings, references, next steps…"
          className="mt-1 w-full border border-ink-700 bg-ink-900 px-2 py-1.5 text-sm text-ink-100 focus:border-sky-500"
        />
      </label>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={run}
          disabled={running || !note.trim()}
          className="border border-ink-600 bg-ink-800 px-3 py-1.5 font-mono text-xs text-ink-100 hover:bg-ink-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {running ? 'Adding…' : 'Add note'}
        </button>
        {running && <Spinner />}
      </div>
      {error ? <ErrorPanel error={error} onRetry={run} /> : null}
    </div>
  )
}