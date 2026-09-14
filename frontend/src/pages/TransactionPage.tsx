import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import type { DecisionReplay, InvestigationMetadata, PipelineResult, TransactionOverview } from '@/api/types'
import { transactionsApi } from '@/api/transactions'
import { investigationsApi } from '@/api/investigations'
import { ApiRequestError } from '@/api/client'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { DecisionHeader } from '@/components/decision/DecisionHeader'
import { RiskScorePanel } from '@/components/risk/RiskScorePanel'
import { RulesPanel } from '@/components/rules/RulesPanel'
import { PolicyPanel } from '@/components/policy/PolicyPanel'
import { DecisionReplayPanel } from '@/components/replay/DecisionReplayPanel'
import { EvidenceLineage } from '@/components/evidence/EvidenceLineage'
import { PolicyLabPanel } from '@/components/lab/PolicyLabPanel'
import { CounterfactualPanel } from '@/components/lab/CounterfactualPanel'
import { OpenInvestigation } from '@/components/investigation/OpenInvestigation'

export function TransactionPage() {
  const { reference = '' } = useParams()
  const decoded = decodeURIComponent(reference)

  const [overview, setOverview] = useState<TransactionOverview | null>(null)
  const [replay, setReplay] = useState<DecisionReplay | null>(null)
  const [investigations, setInvestigations] = useState<InvestigationMetadata[]>([])
  const [state, setState] = useState<{ loading: boolean; error: unknown }>({ loading: true, error: undefined })

  const load = useCallback(async () => {
    try {
      const ov = await transactionsApi.overview(decoded)
      setOverview(ov)
      if (ov.decided) {
        const [rep, inv] = await Promise.all([
          transactionsApi.decisionReplay(decoded),
          investigationsApi.list(decoded),
        ])
        setReplay(rep)
        setInvestigations(inv)
      } else {
        setReplay(null)
        setInvestigations([])
      }
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

  const [processing, setProcessing] = useState(false)
  const [processError, setProcessError] = useState<unknown>(undefined)
  const [processResult, setProcessResult] = useState<PipelineResult | null>(null)

  const runPipeline = async () => {
    setProcessing(true)
    setProcessError(undefined)
    setProcessResult(null)
    try {
      const result = await transactionsApi.process(decoded)
      setProcessResult(result)
      await load()
    } catch (err) {
      setProcessError(err)
    } finally {
      setProcessing(false)
    }
  }

  if (state.loading) {
    return <main className="px-6 py-10"><Spinner label={`Loading ${decoded}`} /></main>
  }

  if (state.error) {
    const notFound = state.error instanceof ApiRequestError && state.error.status === 404
    return (
      <main className="px-6 py-10">
        <div className="mx-auto max-w-3xl">
          <ErrorPanel
            error={state.error}
            onRetry={retry}
            {...(notFound ? { titleOverride: 'Transaction not found' } : {})}
          />
        </div>
      </main>
    )
  }

  if (!overview) return null

  if (!overview.decided) {
    return (
      <main className="px-6 py-10">
        <div className="mx-auto max-w-3xl border border-ink-700 bg-ink-900 p-5">
          <h1 className="section-label mb-1">Transaction</h1>
          <p className="technical text-lg text-ink-100">
            {decoded} <span className="ml-2 technical text-[11px] text-ink-500">{overview.status}</span>
          </p>
          <p className="mt-2 mb-0 max-w-prose text-[13px] text-ink-300">
            This transaction has not been processed by the intelligence pipeline yet. Running the pipeline records the
            feature snapshot, risk score, decision, and evidence — the data this workspace displays.
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={runPipeline}
              disabled={processing}
              className="border border-sky-500/50 bg-sky-500/10 px-4 py-2 font-mono text-xs uppercase text-sky-300 hover:bg-sky-500/20 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {processing ? 'Processing…' : 'Process transaction'}
            </button>
            {processing && <Spinner label="Running intelligence pipeline" />}
          </div>
          {processResult && !processError && (
            <div className="mt-4 border border-sky-500/40 bg-sky-500/5 p-3 text-[13px]">
              <p className="mt-0 mb-1 text-sky-200">
                Decision recorded: <span className="technical">{processResult.decision}</span> (risk score{' '}
                {processResult.riskScore != null ? processResult.riskScore.toFixed(2) : '—'}).
              </p>
              <p className="mx-0 my-0 text-xs normal-case text-ink-300">{processResult.decisionReason}</p>
              <button
                type="button"
                onClick={load}
                className="mt-2 border border-ink-600 px-2 py-1 font-mono text-xs text-ink-200 hover:border-ink-400"
              >
                Reload workspace
              </button>
            </div>
          )}
          {processError ? <div className="mt-4"><ErrorPanel error={processError} onRetry={runPipeline} /></div> : null}
        </div>
      </main>
    )
  }

  if (!replay) return null

  return (
    <main className="px-6 py-8">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-5">
        <DecisionHeader
          reference={replay.transactionReference}
          transaction={replay.transaction}
          decision={replay.decision}
          riskScore={replay.riskScore}
          model={replay.model}
          policy={replay.policy}
        />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <RiskScorePanel
            riskScore={replay.riskScore}
            model={replay.model}
            featureSnapshot={replay.featureSnapshot}
            riskFactors={replay.riskFactors}
          />
          <RulesPanel rules={replay.triggeredRules} disagreement={replay.disagreement} />
        </div>

        <PolicyPanel policy={replay.policy} riskScore={replay.riskScore} decision={replay.decision} />
        <DecisionReplayPanel replay={replay} />
        <EvidenceLineage nodes={replay.evidence.nodes} edges={replay.evidence.edges} />

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <PolicyLabPanel
            transactionReference={replay.transactionReference}
            policy={replay.policy}
          />
          <CounterfactualPanel
            transactionReference={replay.transactionReference}
            recordedFeatures={replay.featureSnapshot.features}
          />
        </div>

        <OpenInvestigation
          transactionReference={replay.transactionReference}
          existing={investigations}
          onCreated={(created) => setInvestigations((prev) => [created, ...prev])}
        />
      </div>
    </main>
  )
}