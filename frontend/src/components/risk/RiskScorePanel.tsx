import type { FeatureSnapshotInfo, ModelInfo, RiskFactorInfo, RiskScoreInfo } from '@/api/types'
import { formatScore, formatTimestamp } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { Badge, SeverityDot } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'
import { KeyValue, DefinitionList } from '@/components/ui/KeyValue'

interface RiskScorePanelProps {
  riskScore: RiskScoreInfo
  model: ModelInfo
  featureSnapshot: FeatureSnapshotInfo
  riskFactors: RiskFactorInfo[]
}

const factorTone: Record<string, { dot: string; badge: string }> = {
  HIGH: { dot: 'bg-red-500', badge: 'bg-red-500/10 border-red-500/40 text-red-400' },
  MEDIUM: { dot: 'bg-amber-400', badge: 'bg-amber-500/10 border-amber-500/40 text-amber-300' },
  LOW: { dot: 'bg-sky-400', badge: 'bg-sky-500/10 border-sky-500/40 text-sky-300' },
}

const FALLBACK_TONE: { dot: string; badge: string } = {
  dot: 'bg-amber-400',
  badge: 'bg-amber-500/10 border-amber-500/40 text-amber-300',
}

/** Level 2–3: how risky the model said it was, and which factors contributed. */
export function RiskScorePanel({ riskScore, model, featureSnapshot, riskFactors }: RiskScorePanelProps) {
  return (
    <Panel title="Risk assessment">
      <div className="flex flex-wrap items-center gap-x-8 gap-y-2 border-b border-ink-800 pb-3">
        <div>
          <span className="section-label block">Risk score</span>
          <span className="technical text-3xl leading-none text-ink-100">
            {formatScore(riskScore.score)}
            <span className="ml-1 align-middle text-sm text-ink-400">/ 1.00</span>
          </span>
          <span className="mt-1 block technical text-[11px] uppercase tracking-wider text-ink-400">
            {riskScore.prediction}
          </span>
        </div>
        <DefinitionList>
          <KeyValue label="Model" value={`${model.name}/${model.version}`} />
          <KeyValue label="Algorithm" value={model.algorithm ?? '—'} />
          <KeyValue label="Feature schema" value={featureSnapshot.schemaVersion} />
          <KeyValue
            label="Inference"
            value={riskScore.inferenceLatencyMs != null ? `${riskScore.inferenceLatencyMs} ms` : '—'}
          />
        </DefinitionList>
      </div>

      <h3 className="section-label mt-3">Risk factors</h3>
      {riskFactors.length === 0 ? (
        <div className="mt-2">
          <EmptyState message="No risk factors were recorded for this decision." hint="Absence of factors is not a safety claim." />
        </div>
      ) : (
        <ul className="mt-2 grid grid-cols-1 gap-x-6 divide-y divide-ink-800 sm:grid-cols-2">
          {riskFactors.map((factor) => {
            const tone = factorTone[factor.severity.toUpperCase()] ?? FALLBACK_TONE
            return (
              <li key={`${factor.source}-${factor.factorType}`} className="flex items-start gap-3 py-2">
                <span className="mt-1.5">
                  <SeverityDot className={tone.dot} />
                </span>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="technical text-[13px] text-ink-100">{factor.factorType}</span>
                    {factor.severity && (
                      <Badge tone={tone.badge} className="!px-1.5 !py-0 text-[10px]">
                        {factor.severity}
                      </Badge>
                    )}
                  </div>
                  <p className="mt-0.5 mb-0 text-xs text-ink-400">{factor.description}</p>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <div className="mt-3 border-t border-ink-800 pt-2 text-right">
        <span className="section-label">Snapshot</span>{' '}
        <span className="technical text-[11px] text-ink-400">
          {featureSnapshot.id} · {formatTimestamp(featureSnapshot.generatedAt)}
        </span>
      </div>
    </Panel>
  )
}