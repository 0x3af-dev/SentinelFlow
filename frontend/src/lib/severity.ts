import type { Decision } from '@/api/types'

export type Severity = Decision

export interface DecisionTone {
  label: string
  /** semantic tailwind classes (design tokens are CSS-side; these are display-only) */
  text: string
  badge: string
  ring: string
  dot: string
}

/**
 * Display-level mapping from a persisted backend decision to a visual tone.
 * NEVER derives the decision itself — it only styles the value the backend sent.
 */
export const decisionTone: Record<Decision, DecisionTone> = {
  ALLOW: {
    label: 'ALLOW',
    text: 'text-emerald-500',
    badge: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400',
    ring: 'ring-emerald-500/30',
    dot: 'bg-emerald-500',
  },
  REVIEW: {
    label: 'REVIEW',
    text: 'text-amber-400',
    badge: 'bg-amber-500/10 border-amber-500/40 text-amber-300',
    ring: 'ring-amber-500/40',
    dot: 'bg-amber-400',
  },
  BLOCK: {
    label: 'BLOCK',
    text: 'text-red-400',
    badge: 'bg-red-500/10 border-red-500/40 text-red-400',
    ring: 'ring-red-500/40',
    dot: 'bg-red-500',
  },
}

export const decisionDescription: Record<Decision, string> = {
  ALLOW: 'The transaction was permitted by policy.',
  REVIEW: 'The transaction was flagged for manual review.',
  BLOCK: 'The transaction was blocked by policy.',
}

export const changeTypeLabel = (changeType: string): string => {
  switch (changeType) {
    case 'UNCHANGED':
      return 'No change'
    case 'MORE_PERMISSIVE':
      return 'More permissive'
    case 'MORE_RESTRICTIVE':
      return 'More restrictive'
    default:
      return changeType
  }
}

export const priorityTone: Record<string, string> = {
  LOW: 'text-zinc-400',
  MEDIUM: 'text-sky-400',
  HIGH: 'text-amber-400',
  CRITICAL: 'text-red-400',
}