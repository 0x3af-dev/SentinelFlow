import type { ReactNode } from 'react'

interface PanelProps {
  title?: string
  right?: ReactNode
  children: ReactNode
  className?: string
  testId?: string
}

/** Bordered workspace surface. Optional caption + right-slot (actions/stamps). */
export function Panel({ title, right, children, className = '', testId }: PanelProps) {
  return (
    <section data-testid={testId} className={`panel ${className}`}>
      {(title || right) && (
        <header className="flex items-center justify-between gap-3 border-b border-ink-700 px-4 py-2">
          {title ? <h2 className="section-label">{title}</h2> : <span />}
          {right}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function PanelBody({ children }: { children: ReactNode }) {
  return <div className="p-4">{children}</div>
}