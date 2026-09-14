import type { DecisionReplay } from '@/api/types'
import { formatMoney, formatScore, formatTimestamp } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { Stamp } from '@/components/ui/Stamp'
import { DefinitionList, KeyValue } from '@/components/ui/KeyValue'

/**
 * §26–27: read-only reconstruction of the persisted lineage. Explicitly framed
 * as a historical reconstruction, never as "re-running the decision".
 */
export function DecisionReplayPanel({ replay }: { replay: DecisionReplay }) {
  return (
    <Panel
      title="Decision replay"
      right={<Stamp kind="historical" />}
      testId="decision-replay"
    >
      <p className="mt-0 mb-3 text-xs text-ink-400">
        Historical reconstruction of the persisted decision lineage. Read-only; no re-inference is performed.
      </p>

      <div className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2 lg:grid-cols-3">
        <DefinitionList>
          <KeyValue label="Transaction" value={replay.transactionReference} />
          <KeyValue label="Amount" value={formatMoney(replay.transaction.amount, replay.transaction.currency)} />
          <KeyValue label="Status" value={replay.transaction.status} />
          <KeyValue label="At" value={formatTimestamp(replay.transaction.timestamp)} />
        </DefinitionList>
        <DefinitionList>
          <KeyValue label="Feature snapshot" value={replay.featureSnapshot.schemaVersion} />
          <KeyValue label="Feature id" value={replay.featureSnapshot.id} />
          <KeyValue label="Model" value={`${replay.model.name}/${replay.model.version}`} />
          <KeyValue label="Algorithm" value={replay.model.algorithm ?? '—'} />
        </DefinitionList>
        <DefinitionList>
          <KeyValue label="Risk score" value={formatScore(replay.riskScore.score)} />
          <KeyValue label="Prediction" value={replay.riskScore.prediction} />
          <KeyValue label="Policy" value={`${replay.policy.name}/${replay.policy.version}`} />
          <KeyValue label="Decision" value={replay.decision.finalDecision} />
        </DefinitionList>
      </div>

      <div className="mt-3 border-t border-ink-800 pt-2 text-xs text-ink-400">
        <span className="section-label mr-2">Evidence</span>
        <span className="technical">
          {replay.evidence.nodeCount} nodes / {replay.evidence.edgeCount} edges
        </span>
        <span className="ml-4 section-label mr-2">Rules</span>
        <span className="technical">{replay.triggeredRules.length} triggered</span>
        <span className="ml-4 section-label mr-2">Factors</span>
        <span className="technical">{replay.riskFactors.length}</span>
      </div>
    </Panel>
  )
}