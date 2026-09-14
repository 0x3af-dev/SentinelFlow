import { useCallback, useEffect, useState } from 'react'
import type {
  AiInvestigationRun,
  InvestigationExplanation,
  InvestigationRequestType,
} from '@/api/types'
import { investigationsApi } from '@/api/investigations'
import { formatScore, formatTimestamp } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { Stamp } from '@/components/ui/Stamp'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import type { Decision } from '@/api/types'

interface AiInvestigationPanelProps {
  investigationId: string
  transactionReference: string
  /** Evidence node ids that exist in the persisted graph, for resolution markers. */
  evidenceNodeIds?: string[]
}

const PRESETS: { type: InvestigationRequestType; label: string }[] = [
  { type: 'WHY_FLAGGED', label: 'Why flagged?' },
  { type: 'SUMMARIZE', label: 'Summarize' },
  { type: 'RISK_FACTORS', label: 'Driving risk factors' },
  { type: 'CONFLICTS', label: 'Conflicts' },
  { type: 'BEHAVIORAL', label: 'Behavioral' },
  { type: 'NEXT_EVIDENCE', label: 'Next evidence' },
]

function EvidenceIds({ ids, resolved }: { ids: string[]; resolved: Set<string> }) {
  if (ids.length === 0) return <span className="text-[10px] text-ink-600">no evidence cited</span>
  return (
    <span className="flex flex-wrap gap-1">
      {ids.map((id) => {
        const ok = resolved.has(id)
        return (
          <span
            key={id}
            title={ok ? 'Resolves to persisted evidence' : 'Not in the persisted evidence graph'}
            className={`technical font-mono text-[10px] ${ok ? 'text-emerald-400' : 'text-rose-400'}`}
          >
            {id.slice(0, 8)}…
          </span>
        )
      })}
    </span>
  )
}

/** Phase 6: evidence-grounded AI investigator. The model only ever reads
 * evidence through the controlled tool surface and its answer is validated by
 * the backend before it is shown here. Because the AI never decides, the
 * recorded decision stays authoritative; this panel only interprets it.
 */
export function AiInvestigationPanel({
  investigationId,
  transactionReference,
  evidenceNodeIds = [],
}: AiInvestigationPanelProps) {
  const resolved = new Set(evidenceNodeIds)
  const [requestType, setRequestType] = useState<InvestigationRequestType>('SUMMARIZE')
  const [freeForm, setFreeForm] = useState('')
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<unknown>(undefined)
  const [answer, setAnswer] = useState<InvestigationExplanation | null>(null)
  const [runs, setRuns] = useState<AiInvestigationRun[] | null>(null)
  const [runsError, setRunsError] = useState<unknown>(undefined)

  const refreshRuns = useCallback(() => {
    investigationsApi
      .explanationRuns(investigationId)
      .then((next) => {
        setRuns(next)
        setRunsError(undefined)
      })
      .catch((err) => setRunsError(err))
  }, [investigationId])

  useEffect(() => {
    refreshRuns()
  }, [refreshRuns])

  const generate = useCallback(
    async (type: InvestigationRequestType) => {
      if (type === 'FREE_FORM' && freeForm.trim().length === 0) return
      setRunning(true)
      setError(undefined)
      try {
        const next = await investigationsApi.explain(investigationId, {
          requestType: type,
          ...(type === 'FREE_FORM' ? { freeFormQuestion: freeForm } : {}),
        })
        setAnswer(next)
        setRequestType(type)
        refreshRuns()
      } catch (err) {
        setError(err)
      } finally {
        setRunning(false)
      }
    },
    [freeForm, investigationId, refreshRuns],
  )

  const history = (runs ?? []).slice(0, 8)
  const hasFailedRuns = history.some((run) => run.status === 'FAILED')

  return (
    <Panel
      title="AI investigator"
      testId="ai-investigator"
      right={
        <span className="flex flex-wrap items-center gap-2">
          <Stamp kind="ai" />
          <span className="border border-emerald-500/40 bg-emerald-500/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-emerald-300">
            Read-only · never decides
          </span>
        </span>
      }
    >
      <p className="mt-0 mb-3 max-w-prose text-xs text-ink-400">
        An analyst assistant answers questions about transaction{' '}
        <span className="technical text-ink-200">{transactionReference}</span> using only the evidence already
        collected. The persisted decision remains authoritative — this tool never changes it.
      </p>

      <div className="flex flex-wrap items-center gap-2">
        {PRESETS.map((preset) => (
          <button
            key={preset.type}
            type="button"
            disabled={running}
            onClick={() => void generate(preset.type)}
            className={`border px-2 py-1 font-mono text-[11px] hover:text-emerald-300 disabled:cursor-not-allowed disabled:opacity-50 ${
              requestType === preset.type
                ? 'border-emerald-500/50 text-emerald-300'
                : 'border-ink-600 text-ink-300'
            }`}
          >
            {preset.label}
          </button>
        ))}
      </div>

      <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-end">
        <label className="block flex-1">
          <span className="section-label">Free-form question (treated as untrusted data)</span>
          <textarea
            value={freeForm}
            maxLength={500}
            onChange={(e) => setFreeForm(e.target.value)}
            rows={2}
            className="mt-1 w-full resize-y border border-ink-700 bg-ink-900 px-2 py-1.5 font-mono text-sm text-ink-100 focus:border-emerald-500"
            aria-label="Free-form question for the AI investigator"
          />
        </label>
        <button
          type="button"
          disabled={running || freeForm.trim().length === 0}
          onClick={() => void generate('FREE_FORM')}
          className="border border-emerald-500/50 bg-emerald-500/10 px-4 py-1.5 font-mono text-xs uppercase text-emerald-300 hover:bg-emerald-500/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {running ? 'Analyzing…' : 'Ask'}
        </button>
      </div>

      {running && (
        <div className="mt-3">
          <Spinner label="Analyzing evidence" />
        </div>
      )}

      {error && !running ? (
        <div className="mt-3">
          <ErrorPanel error={error} onRetry={() => void generate(requestType)} />
        </div>
      ) : null}

      {answer && !error && !running ? (
        <div className="mt-4 border border-emerald-500/40 bg-ink-800/60 p-3" data-testid="ai-investigator-answer">
          <div className="flex flex-wrap items-center gap-2">
            <span className="section-label">Answer</span>
            <Stamp kind="ai" />
            <span className="ml-auto flex flex-wrap items-center gap-x-3 gap-y-1 normal-case text-[10px] text-ink-500">
              <span>
                {answer.modelMetadata.provider} · {answer.modelMetadata.model}
              </span>
              <span>{formatTimestamp(answer.generatedAt)}</span>
              <span>{answer.modelMetadata.toolCallCount} tool calls</span>
            </span>
          </div>

          <p className="mt-3 mb-3 max-w-prose text-sm text-ink-100">{answer.summary}</p>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <section>
              <h3 className="section-label mb-1">Recorded risk assessment</h3>
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <DecisionBadge decision={answer.riskAssessment.recordedDecision as Decision} />
                <span className="technical text-ink-300">
                  score {formatScore(answer.riskAssessment.recordedRiskScore)} under{' '}
                  {answer.riskAssessment.decisionPolicy ?? 'no policy'}
                </span>
              </div>
              <p className="mt-1 mb-1 text-xs text-ink-300">{answer.riskAssessment.explanation}</p>
              <EvidenceIds ids={answer.riskAssessment.evidenceIds} resolved={resolved} />
            </section>

            <section>
              <h3 className="section-label mb-1">Observations</h3>
              {answer.observations.length === 0 ? (
                <p className="m-0 text-xs italic text-ink-600">None identified.</p>
              ) : (
                <ul className="m-0 divide-y divide-ink-800">
                  {answer.observations.map((obs, idx) => (
                    <li key={idx} className="py-1.5 text-xs text-ink-300">
                      {obs.statement}
                      <div className="mt-1">
                        <EvidenceIds ids={obs.evidenceIds} resolved={resolved} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
            <section>
              <h3 className="section-label mb-1">Model &amp; behavioral findings</h3>
              {answer.modelFindings.length === 0 && answer.behavioralFindings.length === 0 ? (
                <p className="m-0 text-xs italic text-ink-600">None identified.</p>
              ) : (
                <ul className="m-0 divide-y divide-ink-800">
                  {[...answer.modelFindings, ...answer.behavioralFindings].map((finding, idx) => (
                    <li key={idx} className="py-1.5 text-xs text-ink-300">
                      {finding.statement}
                      <div className="mt-1">
                        <EvidenceIds ids={finding.evidenceIds} resolved={resolved} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section>
              <h3 className="section-label mb-1">Rule findings</h3>
              {answer.ruleFindings.length === 0 ? (
                <p className="m-0 text-xs italic text-ink-600">None identified.</p>
              ) : (
                <ul className="m-0 divide-y divide-ink-800">
                  {answer.ruleFindings.map((finding, idx) => (
                    <li key={idx} className="py-1.5 text-xs text-ink-300">
                      <span className="mr-2 technical text-[10px] text-ink-500">{finding.ruleId}</span>
                      {finding.statement}
                      <span className="block text-[10px] normal-case text-ink-500">
                        outcome: {finding.outcome}
                      </span>
                      <div className="mt-1">
                        <EvidenceIds ids={finding.evidenceIds} resolved={resolved} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          {answer.evidenceConflicts.length > 0 && (
            <section className="mt-4">
              <h3 className="section-label mb-1">Evidence conflicts</h3>
              <ul className="m-0 divide-y divide-ink-800">
                {answer.evidenceConflicts.map((conflict, idx) => (
                  <li key={idx} className="py-1.5 text-xs text-ink-300">
                    {conflict.description}
                    <div className="mt-1">
                      <EvidenceIds ids={conflict.evidenceIds} resolved={resolved} />
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {(answer.simulations.length > 0 || answer.counterfactuals.length > 0) && (
            <section className="mt-4">
              <h3 className="section-label mb-1">Hypothetical analyses (do not alter the recorded decision)</h3>
              <ul className="m-0 divide-y divide-ink-800">
                {answer.simulations.map((sim) => (
                  <li key={sim.simulationId} className="py-1.5 text-xs text-ink-300">
                    <span className="mr-2 text-[10px] normal-case text-amber-300">
                      {sim.hypotheticalPolicyName}/{sim.hypotheticalPolicyVersion} → {sim.simulatedDecision}
                    </span>
                    {sim.statement}
                    <p className="mt-1 mb-1 text-[10px] normal-case text-ink-500">{sim.disclaimer}</p>
                    <EvidenceIds ids={sim.evidenceIds} resolved={resolved} />
                  </li>
                ))}
                {answer.counterfactuals.map((cf) => (
                  <li key={cf.analysisId} className="py-1.5 text-xs text-ink-300">
                    <span className="mr-2 text-[10px] normal-case text-sky-300">
                      {cf.feature}: {formatScore(cf.recordedScore)} → {formatScore(cf.hypotheticalScore)} (
                      {cf.hypotheticalDecision})
                    </span>
                    {cf.statement}
                    <p className="mt-1 mb-1 text-[10px] normal-case text-ink-500">{cf.disclaimer}</p>
                    <EvidenceIds ids={cf.evidenceIds} resolved={resolved} />
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <h3 className="section-label mb-1">Uncertainty</h3>
              {answer.uncertainty.length === 0 ? (
                <p className="m-0 text-xs italic text-ink-600">None stated.</p>
              ) : (
                <ul className="m-0 divide-y divide-ink-800">
                  {answer.uncertainty.map((u, idx) => (
                    <li key={idx} className="py-1.5 text-xs text-ink-300">
                      {u.statement}
                      <span className="block text-[10px] normal-case text-ink-500">{u.reason}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <h3 className="section-label mb-1">Recommended next evidence</h3>
              {answer.recommendedNextEvidence.length === 0 ? (
                <p className="m-0 text-xs italic text-ink-600">None.</p>
              ) : (
                <ul className="m-0 divide-y divide-ink-800">
                  {answer.recommendedNextEvidence.map((rec, idx) => (
                    <li key={idx} className="py-1.5 text-xs text-ink-300">
                      {rec.request}
                      <span className="block text-[10px] normal-case text-ink-500">{rec.rationale}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>

          {answer.evidenceReferences.length > 0 && (
            <section className="mt-4 border-t border-ink-700 pt-2">
              <h3 className="section-label mb-1">Cited evidence</h3>
              <ul className="m-0 divide-y divide-ink-800">
                {answer.evidenceReferences.map((ref) => (
                  <li key={ref.evidenceId} className="flex flex-wrap items-center gap-x-2 gap-y-1 py-1 text-[11px]">
                    <span className="technical font-mono text-emerald-400">{ref.evidenceId.slice(0, 8)}…</span>
                    <span className="text-ink-300">{ref.description}</span>
                    <span className="ml-auto normal-case text-[10px] text-ink-500">
                      {ref.sourceType} · {ref.sourceId}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      ) : null}

      <div className="mt-4 border-t border-ink-800 pt-3">
        <h3 className="section-label mb-1">Prior runs</h3>
        {runsError && runs === null ? <ErrorPanel error={runsError} onRetry={refreshRuns} /> : null}
        {history.length === 0 && runs !== null && !runsError ? (
          <p className="m-0 text-xs italic text-ink-600">No AI investigations have run for this case yet.</p>
        ) : null}
        <ul className="m-0 divide-y divide-ink-800">
          {history.map((run) => (
            <li key={run.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-1.5 text-[11px]">
              <span
                className={`border px-1.5 py-0.5 font-mono text-[9px] tracking-wider ${
                  run.status === 'SUCCEEDED'
                    ? 'border-emerald-500/50 text-emerald-300'
                    : 'border-rose-500/50 text-rose-300'
                }`}
              >
                {run.status}
              </span>
              <span className="technical text-ink-300">{run.requestType}</span>
              {run.provider && <span className="text-ink-500">{run.provider}</span>}
              <span className="text-ink-500">{run.toolCallCount} calls</span>
              {run.latencyMs != null && <span className="text-ink-500">{run.latencyMs}ms</span>}
              {run.status === 'FAILED' && run.errorCode && (
                <span className="normal-case text-[10px] text-rose-300">
                  {run.errorCode}: {run.errorMessage}
                </span>
              )}
              <span className="ml-auto technical text-ink-500">{formatTimestamp(run.createdAt)}</span>
            </li>
          ))}
        </ul>
        {hasFailedRuns && (
          <p className="m-0 mt-1 text-[10px] normal-case text-rose-300">
            Failed runs are recorded on the server; the production decision is never affected.
          </p>
        )}
      </div>
    </Panel>
  )
}