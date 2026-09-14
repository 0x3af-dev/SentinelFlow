import type { ReactNode } from 'react'

interface BadgeProps {
  tone?: string
  children: ReactNode
  className?: string
  title?: string
}

/** Compact inline label; tone is a "bg-…/border-…/text-…" triplet supplied by the caller. */
export function Badge({ tone = 'bg-ink-800 border-ink-600 text-ink-200', children, className = '', title }: BadgeProps) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1.5 border px-2 py-0.5 font-mono text-[11px] tracking-wide ${tone} ${className}`}
    >
      {children}
    </span>
  )
}

export function SeverityDot({ className }: { className: string }) {
  return <span aria-hidden="true" className={`inline-block h-1.5 w-1.5 ${className}`} />
}