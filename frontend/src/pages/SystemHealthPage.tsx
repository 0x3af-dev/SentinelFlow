import { useCallback, useEffect, useState } from 'react'
import { operationsApi, type DependencyStatus, type OperationalSummary, type IntegrityResponse } from '@/api/operations'
import { Panel } from '@/components/ui/Panel'
import { Badge } from '@/components/ui/Badge'
import { KeyValue, DefinitionList } from '@/components/ui/KeyValue'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorPanel } from '@/components/ui/ErrorPanel'

const STATUS_TONE: Record<DependencyStatus, string> = {
  HEALTHY: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400',
  DEGRADED: 'border-amber-500/40 bg-amber-500/10 text-amber-400',
  UNAVAILABLE: 'border-red-500/40 bg-red-500/10 text-red-400',
  DISABLED: 'border-ink-600 bg-ink-800 text-ink-400',
  UNKNOWN: 'border-ink-600 bg-ink-800 text-ink-400',
}

function StatusBadge({ status, detail }: { status: DependencyStatus; detail: string }) {
  return (
    <Badge tone={STATUS_TONE[status]} title={detail}>
      {status}
    </Badge>
  )
}

function MetricCard({ label, value, unit }: { label: string; value: number | string; unit?: string }) {
  return (
    <div className="border border-ink-700 bg-ink-900 px-3 py-2">
      <p className="m-0 font-mono text-[11px] text-ink-400">{label}</p>
      <p className="m-0 mt-0.5 font-mono text-lg font-semibold text-ink-100">
        {typeof value === 'number' ? Math.round(value).toLocaleString() : value}
        {unit && <span className="ml-1 text-[11px] font-normal text-ink-400">{unit}</span>}
      </p>
    </div>
  )
}

export function SystemHealthPage() {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(undefined)
  const [summary, setSummary] = useState<OperationalSummary | null>(null)
  const [integrity, setIntegrity] = useState<IntegrityResponse | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(undefined)
    try {
      const [s, i] = await Promise.all([operationsApi.summary(), operationsApi.integrity()])
      setSummary(s)
      setIntegrity(i)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <div className="px-6 py-10"><Spinner label="Loading system health…" /></div>
  if (error) return <div className="px-6 py-10"><ErrorPanel error={error} onRetry={load} /></div>
  if (!summary) return null

  const deps = Object.entries(summary.dependencies)

  return (
    <div className="mx-auto max-w-5xl space-y-4 px-6 py-8" data-testid="system-health">
      <div className="flex items-center justify-between">
        <h1 className="section-label">System Health</h1>
        <button
          type="button"
          onClick={load}
          className="border border-ink-600 bg-ink-800 px-3 py-1 font-mono text-xs text-ink-200 hover:bg-ink-700"
        >
          Refresh
        </button>
      </div>

      <Panel title="Dependencies">
        {deps.length === 0 ? (
          <p className="m-0 text-[13px] text-ink-400">No dependency probes configured.</p>
        ) : (
          <DefinitionList>
            {deps.map(([name, probe]) => (
              <div key={name} className="flex items-center justify-between gap-3 py-1">
                <dt className="shrink-0 text-xs text-ink-400" style={{ minWidth: '6rem' }}>{name}</dt>
                <dd className="m-0 text-right text-[13px]">
                  <StatusBadge status={probe.status} detail={probe.detail} />
                </dd>
              </div>
            ))}
          </DefinitionList>
        )}
      </Panel>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MetricCard label="Outbox pending" value={summary.outbox.pending} />
        <MetricCard label="Outbox failed" value={summary.outbox.failed} />
        <MetricCard label="DLQ count" value={summary.dlq.count} />
        <MetricCard label="Stuck processing" value={summary.attempts.stuckProcessingCount} />
      </div>

      <Panel title="Transaction Metrics">
        <DefinitionList>
          <KeyValue label="Processed" value={Math.round(summary.counters.transactionProcessed)} />
          <KeyValue label="Succeeded" value={Math.round(summary.counters.transactionSucceeded)} />
          <KeyValue label="Failed" value={Math.round(summary.counters.transactionFailed)} />
          {Object.entries(summary.counters.decisionsByType).map(([type, count]) => (
            <KeyValue key={type} label={`Decision: ${type}`} value={Math.round(count)} />
          ))}
        </DefinitionList>
      </Panel>

      <Panel title="Kafka Metrics">
        <DefinitionList>
          <KeyValue label="Consumed" value={Math.round(summary.counters.kafkaConsumed)} />
          <KeyValue label="Succeeded" value={Math.round(summary.counters.kafkaSucceeded)} />
          <KeyValue label="Duplicate" value={Math.round(summary.counters.kafkaDuplicate)} />
          <KeyValue label="Retryable" value={Math.round(summary.counters.kafkaRetryable)} />
          <KeyValue label="Permanent" value={Math.round(summary.counters.kafkaPermanent)} />
          <KeyValue label="Dead-lettered" value={Math.round(summary.counters.kafkaDeadLettered)} />
        </DefinitionList>
      </Panel>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
        <Panel title="Latency (mean)">
          <DefinitionList>
            <KeyValue label="Transaction" value={`${Math.round(summary.latencyMs.transactionMeanMs)} ms`} />
            <KeyValue label="Kafka" value={`${Math.round(summary.latencyMs.kafkaMeanMs)} ms`} />
            <KeyValue label="ML" value={`${Math.round(summary.latencyMs.mlMeanMs)} ms`} />
            <KeyValue label="AI" value={`${Math.round(summary.latencyMs.aiMeanMs)} ms`} />
          </DefinitionList>
        </Panel>

        <Panel title="ML / AI">
          <DefinitionList>
            <KeyValue label="ML requests" value={Math.round(summary.counters.mlRequests)} />
            <KeyValue label="ML success" value={Math.round(summary.counters.mlSuccess)} />
            <KeyValue label="ML failure" value={Math.round(summary.counters.mlFailure)} />
            <KeyValue label="AI requests" value={Math.round(summary.counters.aiRequests)} />
            <KeyValue label="AI success" value={Math.round(summary.counters.aiSuccess)} />
            <KeyValue label="AI failure" value={Math.round(summary.counters.aiFailure)} />
          </DefinitionList>
        </Panel>

        <Panel title="Attempt status">
          <DefinitionList>
            {Object.entries(summary.attempts.statusCounts).map(([status, count]) => (
              <KeyValue key={status} label={status} value={count} />
            ))}
          </DefinitionList>
        </Panel>
      </div>

      <Panel title="Data Integrity" right={integrity ? (
        <Badge tone={integrity.healthyAll ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400' : 'border-red-500/40 bg-red-500/10 text-red-400'}>
          {integrity.healthyAll ? 'ALL HEALTHY' : 'ISSUES FOUND'}
        </Badge>
      ) : undefined}>
        {integrity ? (
          <DefinitionList>
            {integrity.checks.map((check) => (
              <div key={check.id} className="flex items-center justify-between gap-3 py-0.5">
                <dt className="shrink-0 text-xs text-ink-400" style={{ minWidth: '14rem' }}>{check.description}</dt>
                <dd className="m-0 text-right text-[13px]">
                  {check.healthy ? (
                    <Badge tone="border-emerald-500/40 bg-emerald-500/10 text-emerald-400">0 issues</Badge>
                  ) : (
                    <Badge tone="border-red-500/40 bg-red-500/10 text-red-400">{check.issueCount} issues</Badge>
                  )}
                </dd>
              </div>
            ))}
          </DefinitionList>
        ) : (
          <p className="m-0 text-[13px] text-ink-400">Integrity check not available.</p>
        )}
      </Panel>
    </div>
  )
}
