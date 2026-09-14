import { useEffect, useMemo, useState } from 'react'
import type {
  CounterfactualFeatureSpec,
  CounterfactualRequest,
  CounterfactualResponse,
} from '@/api/types'
import { analyticsApi } from '@/api/analytics'
import { formatScore, formatTimestamp, formatValue } from '@/lib/format'
import { changeTypeLabel } from '@/lib/severity'
import { Panel } from '@/components/ui/Panel'
import { Stamp } from '@/components/ui/Stamp'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

interface CounterfactualPanelProps {
  transactionReference: string
  /** Feature snapshot recorded at decision time (read-only table of originals). */
  recordedFeatures: Record<string, unknown>
}

function toNumber(value: unknown): number | null {
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

/**
 * §38–44: only backend-supported features are editable; every result carries
 * the mandatory hypothetical disclaimer. The recorded feature snapshot is
 * never mutated — the UI only submits a proposed modification list.
 */
export function CounterfactualPanel({ transactionReference, recordedFeatures }: CounterfactualPanelProps) {
  const [features, setFeatures] = useState<CounterfactualFeatureSpec[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(undefined)
  const [values, setValues] = useState<Record<string, string>>({})
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<unknown>(undefined)
  const [result, setResult] = useState<CounterfactualResponse | null>(null)
  const [history, setHistory] = useState<CounterfactualResponse[] | null>(null)

  useEffect(() => {
    let cancelled = false
    analyticsApi
      .counterfactualFeatures()
      .then((specs) => {
        if (cancelled) return
        setFeatures(specs)
        const next: Record<string, string> = {}
        for (const spec of specs) {
          const original = toNumber(recordedFeatures[spec.name])
          next[spec.name] = original != null ? String(original) : ''
        }
        setValues(next)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err)
      })
    return () => {
      cancelled = true
    }
  }, [transactionReference, recordedFeatures])

  const modifiedCount = useMemo(() => {
    let count = 0
    for (const spec of features ?? []) {
      const original = toNumber(recordedFeatures[spec.name])
      const next = toNumber(values[spec.name])
      if (next !== null && next !== original) count += 1
    }
    return count
  }, [features, values, recordedFeatures])

  if (loadError) {
    return (
      <Panel title="Counterfactual analysis" testId="counterfactual-panel">
        <ErrorPanel error={loadError} onRetry={() => window.location.reload()} />
      </Panel>
    )
  }

  if (features === null) {
    return (
      <Panel title="Counterfactual analysis" testId="counterfactual-panel">
        <Spinner label="Loading supported features" />
      </Panel>
    )
  }

  const run = async () => {
    if (modifiedCount === 0) return
    setRunning(true)
    setError(undefined)
    setResult(null)
    const modifications: CounterfactualRequest['modifications'] = []
    for (const spec of features) {
      const original = toNumber(recordedFeatures[spec.name])
      const next = toNumber(values[spec.name])
      if (next !== null && next !== original) {
        modifications.push({ feature: spec.name, value: next })
      }
    }
    try {
      const response = await analyticsApi.runCounterfactual({ transactionReference, modifications })
      setResult(response)
      setHistory((prev) => [response, ...(prev ?? [])])
    } catch (err) {
      setError(err)
    } finally {
      setRunning(false)
    }
  }

  const reset = () => {
    const next: Record<string, string> = {}
    for (const spec of features) {
      const original = toNumber(recordedFeatures[spec.name])
      next[spec.name] = original != null ? String(original) : ''
    }
    setValues(next)
    setResult(null)
    setError(undefined)
  }

  const historyVisible = (history ?? []).slice(0, 5)

  return (
    <Panel title="Counterfactual analysis" testId="counterfactual-panel">
      <div className="flex flex-wrap gap-2">
        <Stamp kind="counterfactual" />
        <span className="border border-sky-500/50 bg-sky-500/10 px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-sky-300">
          Hypothetical — recorded features are read-only
        </span>
      </div>

      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[560px] border-collapse text-xs">
          <thead>
            <tr className="border-b border-ink-700 text-left">
              <th className="section-label py-1 pr-3 font-normal">Feature</th>
              <th className="section-label py-1 pr-3 font-normal">Recorded</th>
              <th className="section-label py-1 pr-3 font-normal">Hypothetical</th>
              <th className="section-label py-1 font-normal">Bounds</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-800">
            {features.map((spec) => {
              const original = toNumber(recordedFeatures[spec.name])
              return (
                <tr key={spec.name}>
                  <td className="py-1.5 pr-3 align-top">
                    <span className="technical text-ink-100">{spec.name}</span>
                    <p className="mx-0 my-0.5 text-[11px] text-ink-500">{spec.description}</p>
                  </td>
                  <td className="py-1.5 pr-3 technical text-ink-400">
                    {original != null ? formatValue(original) : '—'}
                  </td>
                  <td className="py-1.5 pr-3">
                    <input
                      type="number"
                      value={values[spec.name] ?? ''}
                      min={spec.min}
                      max={spec.max}
                      step={spec.integral ? 1 : 'any'}
                      onChange={(e) => setValues((prev) => ({ ...prev, [spec.name]: e.target.value }))}
                      className="w-28 border border-ink-700 bg-ink-900 px-2 py-1 font-mono text-xs text-ink-100 focus:border-sky-500"
                      aria-label={`${spec.name} hypothetical value`}
                    />
                  </td>
                  <td className="py-1.5 technical text-[10px] text-ink-500">
                    {formatScore(spec.min)} – {formatScore(spec.max)}
                    {spec.integral ? ' · int' : ''}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={run}
          disabled={running || modifiedCount === 0}
          className="border border-sky-500/50 bg-sky-500/10 px-4 py-1.5 font-mono text-xs uppercase text-sky-300 hover:bg-sky-500/20 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {running ? 'Computing…' : 'Run hypothetical'}
        </button>
        <button
          type="button"
          onClick={reset}
          disabled={running}
          className="border border-ink-600 px-3 py-1.5 font-mono text-xs text-ink-300 hover:bg-ink-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Reset to recorded
        </button>
        {modifiedCount > 0 && (
          <span className="technical text-[11px] text-sky-400">{modifiedCount} feature(s) modified</span>
        )}
      </div>

      {running && <div className="mt-3"><Spinner label="Running counterfactual analysis" /></div>}
      {error && !running ? (
        <div className="mt-3">
          <ErrorPanel error={error} onRetry={run} />
        </div>
      ) : null}

      {result && !error ? (
        <div className="mt-4 border border-sky-500/40 bg-ink-800/60 p-3" data-testid="counterfactual-result">
          <div className="flex flex-wrap items-center gap-3">
            <div>
              <span className="section-label block">Recorded</span>
              <span className="technical text-lg text-ink-100">{formatScore(result.originalRiskScore)}</span>
              <div className="mt-1"><DecisionBadge decision={result.originalDecision} /></div>
            </div>
            <span aria-hidden="true" className="text-ink-500">→</span>
            <div>
              <span className="section-label block">Hypothetical</span>
              <span className="technical text-lg text-sky-200">{formatScore(result.hypotheticalRiskScore)}</span>
              <div className="mt-1"><DecisionBadge decision={result.hypotheticalDecision} /></div>
            </div>
            <span
              className={`border px-2 py-1 font-mono text-[11px] ${
                result.scoreDelta >= 0
                  ? 'border-red-500/40 bg-red-500/10 text-red-400'
                  : 'border-sky-500/40 bg-sky-500/10 text-sky-300'
              }`}
            >
              Δ {result.scoreDelta >= 0 ? '+' : ''}
              {formatScore(result.scoreDelta)}
            </span>
            {result.decisionChanged && (
              <span className="text-[11px] normal-case text-amber-300">{changeTypeLabel(result.changeType)}</span>
            )}
          </div>

          {result.appliedModifications.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-2">
              {result.appliedModifications.map((mod) => (
                <li key={mod.feature} className="border border-ink-700 bg-ink-900 px-2 py-1 font-mono text-[10px] text-ink-300">
                  {mod.feature}: <span className="text-ink-500">{formatValue(mod.originalValue)}</span> →{' '}
                  <span className="text-sky-300">{formatValue(mod.modifiedValue)}</span>
                </li>
              ))}
            </ul>
          )}

          <p className="mt-3 mb-0 max-w-prose border-l-2 border-sky-500/60 pl-3 text-xs text-ink-300">
            {result.disclaimer}
          </p>
        </div>
      ) : null}

      {historyVisible.length > 0 && (
        <div className="mt-4 border-t border-ink-800 pt-3">
          <h3 className="section-label mb-2">Recent analyses</h3>
          <ul className="m-0 divide-y divide-ink-800">
            {historyVisible.map((entry) => (
              <li key={entry.analysisId} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-1.5 text-xs">
                <span className="technical text-ink-300">
                  {formatScore(entry.originalRiskScore)} → {formatScore(entry.hypotheticalRiskScore)}
                </span>
                <span className="text-ink-500">·</span>
                <DecisionBadge decision={entry.hypotheticalDecision} />
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