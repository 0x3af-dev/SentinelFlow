import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Link } from 'react-router-dom'
import type {
  DecisionReplay,
  EvidenceGraph,
  InvestigationMetadata,
  InvestigationSummary,
  TimelineEntry,
} from '@/api/types'
import { investigationsApi } from '@/api/investigations'
import { ApiRequestError } from '@/api/client'
import { formatTimestamp } from '@/lib/format'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { DecisionHeader } from '@/components/decision/DecisionHeader'
import { RiskScorePanel } from '@/components/risk/RiskScorePanel'
import { RulesPanel } from '@/components/rules/RulesPanel'
import { PolicyPanel } from '@/components/policy/PolicyPanel'
import { EvidenceLineage } from '@/components/evidence/EvidenceLineage'
import { InvestigationTimeline } from '@/components/timeline/InvestigationTimeline'
import { PolicyLabPanel } from '@/components/lab/PolicyLabPanel'
import { CounterfactualPanel } from '@/components/lab/CounterfactualPanel'
import { AiInvestigationPanel } from '@/components/ai/AiInvestigationPanel'
import { AddEventNote } from '@/components/investigation/AddEventNote'

interface InvestigationLoaded {
  metadata: InvestigationMetadata
  summary: InvestigationSummary
  timeline: TimelineEntry[]
  evidence: EvidenceGraph
  replay: DecisionReplay
}

export function InvestigationPage() {
  const { id = '' } = useParams()
  const decoded = decodeURIComponent(id)

  const [data, setData] = useState<InvestigationLoaded | null>(null)
  const [state, setState] = useState<{ loading: boolean; error: unknown }>({ loading: true, error: undefined })

  const load = useCallback(async () => {
    try {
      const [metadata, summary, timeline, evidence, replay] = await Promise.all([
        investigationsApi.get(decoded),
        investigationsApi.summary(decoded),
        investigationsApi.timeline(decoded),
        investigationsApi.evidence(decoded),
        investigationsApi.replay(decoded),
      ])
      setData({ metadata, summary, timeline, evidence, replay })
      setState({ loading: false, error: undefined })
    } catch (err) {
      setState({ loading: false, error: err })
    }
  }, [decoded])

  const retry = useCallback(() => {
    setState({ loading: true, error: undefined })
    void load()
  }, [load])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch-on-mount; setState only after await
    void load()
  }, [load])

  const refetchTimeline = useCallback(async () => {
    try {
      const timeline = await investigationsApi.timeline(decoded)
      setData((prev) => (prev ? { ...prev, timeline } : prev))
    } catch {
      // Non-fatal: the workspace stays usable with the last known timeline.
    }
  }, [decoded])

  if (state.loading) {
    return <main className="px-6 py-10"><Spinner label={`Loading investigation ${decoded}`} /></main>
  }

  if (state.error) {
    const notFound = state.error instanceof ApiRequestError && state.error.status === 404
    return (
      <main className="px-6 py-10">
        <div className="mx-auto max-w-3xl">
          <ErrorPanel
            error={state.error}
            onRetry={retry}
            {...(notFound ? { titleOverride: 'Investigation not found' } : {})}
          />
        </div>
      </main>
    )
  }

  if (!data) return null

  const { metadata, summary, timeline, evidence, replay } = data

  return (
    <main className="px-6 py-8">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-5">
        <div className="flex flex-wrap items-center gap-3 border border-ink-700 bg-ink-900 px-5 py-3">
          <div>
            <span className="section-label block">Investigation</span>
            <span className="technical text-sm text-ink-100">{metadata.investigationReference}</span>
          </div>
          <span className="border border-ink-600 px-2 py-0.5 font-mono text-[10px] text-ink-300">
            {metadata.status}
          </span>
          <span className="border border-ink-600 px-2 py-0.5 font-mono text-[10px] text-amber-300">
            {metadata.priority}
          </span>
          {metadata.assignedTo && (
            <span className="technical text-[11px] text-ink-400">assigned {metadata.assignedTo}</span>
          )}
          <span className="ml-auto technical text-[11px] text-ink-500">
            opened {formatTimestamp(metadata.openedAt)}
          </span>
          <Link
            to={`/transactions/${encodeURIComponent(summary.transactionReference)}`}
            className="technical text-[11px] text-sky-300 hover:text-sky-200"
          >
            Transaction workspace →
          </Link>
        </div>

        <DecisionHeader
          reference={summary.transactionReference}
          transaction={summary.transaction}
          decision={summary.decision}
          riskScore={summary.riskScore}
          model={replay.model}
          policy={replay.policy}
        />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <RiskScorePanel
            riskScore={summary.riskScore}
            model={replay.model}
            featureSnapshot={replay.featureSnapshot}
            riskFactors={summary.riskFactors}
          />
          <RulesPanel rules={summary.triggeredRules} disagreement={summary.disagreement} />
        </div>

        <PolicyPanel policy={replay.policy} riskScore={summary.riskScore} decision={summary.decision} />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <EvidenceLineage nodes={evidence.nodes} edges={evidence.edges} />
          <InvestigationTimeline
            events={timeline}
            replay={replay}
            simulations={summary.policySimulations}
            counterfactuals={summary.counterfactuals}
          />
        </div>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <PolicyLabPanel transactionReference={summary.transactionReference} policy={replay.policy} />
          <CounterfactualPanel
            transactionReference={summary.transactionReference}
            recordedFeatures={replay.featureSnapshot.features}
          />
        </div>

        <AiInvestigationPanel
          investigationId={decoded}
          transactionReference={summary.transactionReference}
          evidenceNodeIds={evidence.nodes.map((node) => node.id)}
        />

        <div className="border-t border-ink-800 pt-1">
          <AddEventNote investigationId={decoded} onAdded={() => void refetchTimeline()} />
        </div>
      </div>
    </main>
  )
}