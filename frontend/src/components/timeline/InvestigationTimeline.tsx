import type { CounterfactualResponse, DecisionReplay, PolicySimulationResponse, TimelineEntry } from '@/api/types'
import { formatScore, formatTimestamp, formatValue } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { EmptyState } from '@/components/ui/EmptyState'

interface InvestigationTimelineProps {
  /** Investigation events in backend order (any direction; sorted here). */
  events: TimelineEntry[]
  replay: DecisionReplay | null
  simulations: PolicySimulationResponse[]
  counterfactuals: CounterfactualResponse[]
}

const EVENT_LABELS: Record<string, string> = {
  INVESTIGATION_CREATED: 'Investigation opened',
  NOTE_ADDED: 'Note added',
  POLICY_SIMULATION_EXECUTED: 'Policy simulation executed',
  COUNTERFACTUAL_EXECUTED: 'Counterfactual analysis executed',
  STATUS_CHANGED: 'Status changed',
  ASSIGNMENT_CHANGED: 'Assignment changed',
}

interface Row {
  id: string
  when: string
  source: string
  title: string
  detail?: string
  decision?: 'ALLOW' | 'REVIEW' | 'BLOCK'
  payload?: Record<string, unknown>
}

/**
 * Merges recorded investigation events with the immutable decision artifacts
 * (risk scoring, policy decision, simulations, counterfactuals). Facts come
 * from the backend records — nothing is computed here.
 */
export function InvestigationTimeline({ events, replay, simulations, counterfactuals }: InvestigationTimelineProps) {
  const rows: Row[] = []

  if (replay) {
    rows.push({
      id: 'fact:score',
      when: replay.riskScore.inferenceTimestamp,
      source: 'Decision artifact',
      title: `Risk scored ${formatScore(replay.riskScore.score)} (${replay.riskScore.prediction} by ${replay.model.name}/${replay.model.version})`,
    })
    rows.push({
      id: 'fact:decision',
      when: replay.decision.decisionTimestamp,
      source: 'Decision artifact',
      title: `Policy decision recorded`,
      detail: replay.decision.reason,
      decision: replay.decision.finalDecision,
    })
  }

  for (const sim of simulations) {
    rows.push({
      id: `sim:${sim.simulationId}`,
      when: sim.createdAt,
      source: 'Policy simulation',
      title: `Policy simulated for ${sim.transactionReference}`,
      detail: `Thresholds review ${formatScore(sim.reviewThreshold)} · block ${formatScore(sim.blockThreshold)} — simulated decision ${sim.simulatedDecision}${
        sim.decisionChanged ? ` (${sim.changeType})` : ' (unchanged)'
      }`,
      decision: sim.simulatedDecision,
    })
  }

  for (const count of counterfactuals) {
    rows.push({
      id: `cf:${count.analysisId}`,
      when: count.createdAt,
      source: 'Counterfactual',
      title: `Hypothetical analysis for ${count.transactionReference}`,
      detail: `Score ${formatScore(count.originalRiskScore)} → ${formatScore(count.hypotheticalRiskScore)} (${
        count.scoreDelta >= 0 ? '+' : ''
      }${formatScore(count.scoreDelta)}); hypothetical decision ${count.hypotheticalDecision}${
        count.decisionChanged ? ` (${count.changeType})` : ' (unchanged)'
      }`,
      decision: count.hypotheticalDecision,
    })
  }

  for (const event of events) {
    const title = EVENT_LABELS[event.eventType] ?? event.eventType
    const payloadKeys = Object.keys(event.payload ?? {})
    const primary = payloadKeys.find((k) => k === 'note') ?? payloadKeys[0]
    const detail = primary ? formatValue(event.payload?.[primary]) : undefined
    rows.push({
      id: `event:${event.eventId}`,
      when: event.eventTimestamp,
      source: `Investigation event · ${event.actorType}${event.actorReference ? ` ${event.actorReference}` : ''}`,
      title,
      ...(payloadKeys.length > 0 && detail !== undefined
        ? { detail: `${primary ?? 'payload'}: ${detail}` }
        : {}),
      payload: event.payload,
    })
  }

  rows.sort((a, b) => (a.when < b.when ? -1 : a.when > b.when ? 1 : 0))

  return (
    <Panel
      title="Timeline"
      right={<span className="technical text-[11px] text-ink-400">{rows.length} entries</span>}
      testId="investigation-timeline"
    >
      {rows.length === 0 ? (
        <EmptyState message="No timeline entries recorded yet." hint="Run a simulation, counterfactual, or add a note to build the picture." />
      ) : (
        <ol className="relative m-0 space-y-4 border-l border-ink-700 pl-4">
          {rows.map((row) => (
            <li key={row.id} className="relative">
              <span
                aria-hidden="true"
                className="absolute -left-[21px] top-1.5 h-2 w-2 rounded-full border border-ink-950 bg-ink-500"
              />
              <div className="flex flex-wrap items-center gap-2">
                <span className="technical text-[13px] text-ink-100">{row.title}</span>
                {row.decision && <DecisionBadge decision={row.decision} />}
                <span className="ml-auto technical text-[10px] text-ink-500">{formatTimestamp(row.when)}</span>
              </div>
              <p className="mx-0 mb-1 mt-0.5 max-w-prose text-xs normal-case text-ink-300">{row.detail}</p>
              <p className="mx-0 mb-0 mt-0 text-[10px] uppercase tracking-wider text-ink-500">
                {row.source} · <span className="technical lowercase">{row.when}</span>
              </p>
            </li>
          ))}
        </ol>
      )}
    </Panel>
  )
}