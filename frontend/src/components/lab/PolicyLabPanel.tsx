import { useState } from 'react'
import type { PolicyInfo, PolicySimulationResponse } from '@/api/types'
import { analyticsApi } from '@/api/analytics'
import { formatScore, formatTimestamp } from '@/lib/format'
import { changeTypeLabel } from '@/lib/severity'
import { Panel } from '@/components/ui/Panel'
import { Stamp } from '@/components/ui/Stamp'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

interface PolicyLabPanelProps {
  transactionReference: string
  policy: PolicyInfo
}

const parsePercent = (value: string): number | null => {
  const n = Number(value)
  if (!Number.isFinite(n)) return null
  return n
}

/**
 * §45–49: the policy lab never touches production. It only proposes alternate
 * thresholds and shows how the recorded score would decide under them.
 */
export function PolicyLabPanel({ transactionReference, policy }: PolicyLabPanelProps) {
  const defaultReview = policy.reviewThreshold != null ? Math.round(policy.reviewThreshold * 100) : 50
  const defaultBlock = policy.blockThreshold != null ? Math.round(policy.blockThreshold * 100) : 85

  const [review, setReview] = useState(String(defaultReview))
  const [block, setBlock] = useState(String(defaultBlock))
  const [error, setError] = useState<unknown>(undefined)
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<PolicySimulationResponse | null>(null)
  const [history, setHistory] = useState<PolicySimulationResponse[] | null>(null)
  const [asked, setAsked] = useState(false)

  const reviewPct = parsePercent(review)
  const blockPct = parsePercent(block)
  const valid = reviewPct !== null && blockPct !== null && reviewPct > 0 && reviewPct < blockPct && blockPct < 100

  if (!asked) {
    return (
      <Panel title="Policy lab" testId="policy-lab">
        <div className="flex flex-wrap gap-2">
          <Stamp kind="simulation" />
          <span className="border border-amber-500/50 bg-amber-500/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-amber-300">
            Not a production policy change
          </span>
        </div>
        <p className="mt-2 mb-0 text-xs text-ink-400">
          Propose alternate thresholds and see how the recorded risk score would decide. Nothing here updates the
          production policy.
        </p>
        <button
          type="button"
          onClick={() => setAsked(true)}
          className="mt-3 border border-ink-600 bg-ink-800 px-3 py-1.5 font-mono text-xs text-ink-100 hover:bg-ink-700"
        >
          Open lab
        </button>
      </Panel>
    )
  }

  const runSimulation = async () => {
    if (!valid || reviewPct === null || blockPct === null) return
    setRunning(true)
    setError(undefined)
    try {
      const response = await analyticsApi.simulatePolicy({
        transactionReference,
        policyName: policy.name,
        policyVersion: policy.version,
        reviewThreshold: reviewPct / 100,
        blockThreshold: blockPct / 100,
      })
      setResult(response)
      const nextHistory = [response, ...(history ?? [])]
      setHistory(nextHistory)
    } catch (err) {
      setError(err)
    } finally {
      setRunning(false)
    }
  }

  const historyVisible = (history ?? []).slice(0, 5)

  return (
    <Panel title="Policy lab" testId="policy-lab">
      <div className="flex flex-wrap gap-2">
        <Stamp kind="simulation" />
        <span className="border border-amber-500/50 bg-amber-500/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-amber-300">
          Not a production policy change
        </span>
      </div>
      <p className="mt-2 mb-0 text-xs text-ink-400">
        For transaction <span className="technical text-ink-200">{transactionReference}</span>, current policy{' '}
        <span className="technical text-ink-200">
          {policy.name}/{policy.version}
        </span>{' '}
        with thresholds review{' '}
        {policy.reviewThreshold != null ? formatScore(policy.reviewThreshold) : '—'}· block{' '}
        {policy.blockThreshold != null ? formatScore(policy.blockThreshold) : '—'}.
      </p>

      <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-[1fr_1fr_auto]">
        <label className="block">
          <span className="section-label">Review threshold %</span>
          <input
            type="number"
            min={1}
            max={99}
            value={review}
            onChange={(e) => setReview(e.target.value)}
            className="mt-1 w-full border border-ink-700 bg-ink-900 px-2 py-1.5 font-mono text-sm text-ink-100 focus:border-sky-500"
            aria-label="Review threshold (percent)"
          />
        </label>
        <label className="block">
          <span className="section-label">Block threshold %</span>
          <input
            type="number"
            min={2}
            max={100}
            value={block}
            onChange={(e) => setBlock(e.target.value)}
            className="mt-1 w-full border border-ink-700 bg-ink-900 px-2 py-1.5 font-mono text-sm text-ink-100 focus:border-sky-500"
            aria-label="Block threshold (percent)"
          />
        </label>
        <button
          type="button"
          onClick={runSimulation}
          disabled={running || !valid}
          className="mt-6 border border-amber-500/50 bg-amber-500/10 px-4 font-mono text-xs uppercase text-amber-300 hover:bg-amber-500/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {running ? 'Simulating…' : 'Simulate'}
        </button>
      </div>
      {!valid && (
        <p className="mt-1 text-[11px] text-amber-400">
          Thresholds must satisfy 0 &lt; review &lt; block &lt; 1 (percent form: 0 &lt; review &lt; block &lt; 100).
        </p>
      )}

      {running && <div className="mt-3"><Spinner label="Simulating policy" /></div>}
      {error && !running ? (
        <div className="mt-3">
          <ErrorPanel error={error} onRetry={runSimulation} />
        </div>
      ) : null}

      {result && !error ? (
        <div className="mt-4 border border-amber-500/40 bg-ink-800/60 p-3" data-testid="policy-lab-result">
          <div className="flex flex-wrap items-center gap-2">
            <span className="section-label">Simulated decision</span>
            <DecisionBadge decision={result.simulatedDecision} />
            <Stamp kind="simulation" />
            {result.decisionChanged && (
              <span className="text-[11px] normal-case text-amber-300">
                changed from <DecisionBadge decision={result.actualDecision} /> with {changeTypeLabel(result.changeType)}
              </span>
            )}
          </div>
          <p className="mt-2 mb-0 max-w-prose text-xs text-ink-300">{result.simulatedReason}</p>
          <p className="mt-2 mb-0 normal-case text-[11px] text-ink-500">
            Recorded score {formatScore(result.baseRiskScore)} evaluated at review {formatScore(result.reviewThreshold)} /
            block {formatScore(result.blockThreshold)} · {formatTimestamp(result.createdAt)} · simulation{' '}
            <span className="technical">{result.simulationId}</span>
          </p>
        </div>
      ) : null}

      {historyVisible.length > 0 && (
        <div className="mt-4 border-t border-ink-800 pt-3">
          <h3 className="section-label mb-2">Recent simulations</h3>
          <ul className="m-0 divide-y divide-ink-800">
            {historyVisible.map((entry) => (
              <li key={entry.simulationId} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-1.5 text-xs">
                <span className="technical text-ink-300">
                  review {formatScore(entry.reviewThreshold)} / block {formatScore(entry.blockThreshold)}
                </span>
                <span className="text-ink-500">→</span>
                <DecisionBadge decision={entry.simulatedDecision} />
                {entry.decisionChanged && (
                  <span className="text-[10px] normal-case text-amber-400">{changeTypeLabel(entry.changeType)}</span>
                )}
                <span className="ml-auto technical text-[10px] text-ink-500">{formatTimestamp(entry.createdAt)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Panel>
  )
}