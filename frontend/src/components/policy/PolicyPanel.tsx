import type { DecisionInfo, PolicyInfo, RiskScoreInfo } from '@/api/types'
import { formatScore } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'

interface PolicyPanelProps {
  policy: PolicyInfo
  riskScore: RiskScoreInfo
  decision: DecisionInfo
}

const clamp01 = (value: number): number => Math.min(1, Math.max(0, value))

/**
 * Level 4: how the policy converted the score into an action. The line is
 * drawn from backend-provided thresholds only — no decision logic here.
 */
export function PolicyPanel({ policy, riskScore, decision }: PolicyPanelProps) {
  const review = policy.reviewThreshold
  const block = policy.blockThreshold
  const score = riskScore.score

  const scorePct = `${clamp01(score) * 100}%`
  const reviewPct = review != null ? `${clamp01(review) * 100}%` : null
  const blockPct = block != null ? `${clamp01(block) * 100}%` : null

  return (
    <Panel title="Policy evaluation">
      <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1">
        <span className="technical text-ink-100">
          {policy.name}/{policy.version}
        </span>
        <span className="technical text-xs text-ink-400">
          review {review != null ? formatScore(review) : '—'} · block {block != null ? formatScore(block) : '—'}
        </span>
      </div>

      <div className="mt-3 pb-5">
        <div className="relative h-1.5 w-full bg-ink-700" aria-hidden="true">
          {reviewPct && (
            <span className="absolute top-0 h-1.5 w-0" style={{ left: reviewPct }}>
              <span className="ml-0.5 inline-block h-full w-0.5 border-l border-amber-400/70" />
            </span>
          )}
          {blockPct && (
            <span className="absolute top-0 h-1.5 w-0" style={{ left: blockPct }}>
              <span className="ml-0.5 inline-block h-full w-0.5 border-l border-red-400/70" />
            </span>
          )}
          <span className="absolute top-0 h-1.5 w-1.5 -translate-x-1/2 rounded-full border border-ink-950 bg-sky-400" style={{ left: scorePct }} />
        </div>
        <div className="relative mt-2 h-5 font-mono text-[10px]" aria-hidden="true">
          <span className="absolute -translate-x-1/2 text-ink-400" style={{ left: '0%' }}>0.00</span>
          {reviewPct && (
            <span className="absolute -translate-x-1/2 text-amber-400/90" style={{ left: reviewPct }}>REVIEW</span>
          )}
          {blockPct && (
            <span className="absolute -translate-x-1/2 text-red-400/90" style={{ left: blockPct }}>BLOCK</span>
          )}
          <span className="absolute -translate-x-1/2 font-semibold text-sky-300" style={{ left: scorePct }}>
            {formatScore(score)}
          </span>
          <span className="absolute right-0 -translate-x-1/2 text-ink-400">1.00</span>
        </div>
      </div>

      <div className="border-t border-ink-800 pt-2 text-xs text-ink-400">
        <span className="section-label mr-2">Deterministic result</span>
        <span className="technical text-sky-300">{formatScore(score)}</span>
        <span className="text-ink-500"> was evaluated against policy </span>
        <span className="technical">{decision.finalDecision}</span>
      </div>
    </Panel>
  )
}