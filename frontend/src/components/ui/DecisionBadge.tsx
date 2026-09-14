import type { Decision } from '@/api/types'
import { decisionTone, decisionDescription } from '@/lib/severity'
import { SeverityDot } from './Badge'

export function DecisionBadge({ decision, large = false }: { decision: Decision; large?: boolean }) {
  const tone = decisionTone[decision]
  return (
    <span
      title={decisionDescription[decision]}
      className={`inline-flex items-center border ${tone.badge} ${large ? 'px-3 py-1 text-base tracking-wider' : 'px-2 py-0.5 text-xs tracking-wider'}`}
    >
      <SeverityDot className={tone.dot} />
      {tone.label}
    </span>
  )
}