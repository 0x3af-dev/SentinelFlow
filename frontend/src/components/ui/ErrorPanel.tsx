import { describeError } from '@/api/client'

interface ErrorPanelProps {
  error: unknown
  onRetry?: () => void
  /** Overrides the derived title when the caller has better context. */
  titleOverride?: string
}

export function ErrorPanel({ error, onRetry, titleOverride }: ErrorPanelProps) {
  const { title, detail } = describeError(error)
  return (
    <div
      role="alert"
      className="border border-red-500/40 bg-red-500/5 px-4 py-3"
    >
      <p className="m-0 font-mono text-sm font-semibold text-red-400">{titleOverride ?? title}</p>
      <p className="mt-1 mb-0 text-[13px] text-ink-300">{detail}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 border border-ink-600 px-2 py-1 font-mono text-xs text-ink-200 hover:border-ink-400"
        >
          Retry
        </button>
      )}
    </div>
  )
}