interface EmptyStateProps {
  message: string
  hint?: string
}

/** Concise empty-state. Never implies that missing evidence means safety. */
export function EmptyState({ message, hint }: EmptyStateProps) {
  return (
    <div className="border border-dashed border-ink-700 bg-ink-850 px-4 py-6 text-center" role="status">
      <p className="mt-0 mb-1 text-[13px] text-ink-300">{message}</p>
      {hint && <p className="m-0 text-xs text-ink-400">{hint}</p>}
    </div>
  )
}