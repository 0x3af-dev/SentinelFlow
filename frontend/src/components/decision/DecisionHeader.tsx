import type { DecisionInfo, ModelInfo, PolicyInfo, RiskScoreInfo, TransactionInfo } from '@/api/types'
import { formatMoney, formatScore, formatTimestamp } from '@/lib/format'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { Stamp } from '@/components/ui/Stamp'

interface DecisionHeaderProps {
  reference: string
  transaction: TransactionInfo
  decision: DecisionInfo
  riskScore: RiskScoreInfo
  model?: ModelInfo
  policy?: PolicyInfo
}

/** Level 1: the actual persisted decision. Unmistakable at a glance (§18–20). */
export function DecisionHeader({ reference, transaction, decision, riskScore, model, policy }: DecisionHeaderProps) {
  const modelLabel = model ? `${model.name}/${model.version}` : '—'
  const policyLabel = policy ? `${policy.name}/${policy.version}` : '—'
  return (
    <header className="border-b border-ink-700 bg-ink-900 px-5 py-4">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
        <div>
          <span className="section-label block">Transaction</span>
          <span className="technical text-lg text-ink-100">{reference}</span>
        </div>
        <div className="ml-2 flex items-center gap-2">
          <Stamp kind="actual" />
          <DecisionBadge decision={decision.finalDecision} large />
        </div>
        <div className="ml-auto text-right">
          <span className="section-label block">Amount</span>
          <span className="technical text-lg text-ink-100">{formatMoney(transaction.amount, transaction.currency)}</span>
          <span className="ml-2 technical text-xs text-ink-400">{transaction.transactionType}</span>
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1 border-t border-ink-800 pt-3 text-[13px] sm:grid-cols-4">
        <div>
          <dt className="section-label">Risk score</dt>
          <dd className="technical mt-0.5 text-ink-100">{formatScore(riskScore.score)}</dd>
        </div>
        <div>
          <dt className="section-label">Model</dt>
          <dd className="technical mt-0.5 text-ink-100">{modelLabel}</dd>
        </div>
        <div>
          <dt className="section-label">Policy</dt>
          <dd className="technical mt-0.5 text-ink-100">{policyLabel}</dd>
        </div>
        <div>
          <dt className="section-label">Decided at</dt>
          <dd className="technical mt-0.5 text-ink-100">{formatTimestamp(decision.decisionTimestamp)}</dd>
        </div>
      </dl>

      <p className="mt-3 mb-0 border border-ink-800 bg-ink-850 px-3 py-2 text-[13px] text-ink-200">
        <span className="section-label mr-2">Production reason</span>
        {decision.reason}
      </p>
    </header>
  )
}