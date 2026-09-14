import { get } from './client'

export type DependencyStatus = 'HEALTHY' | 'DEGRADED' | 'UNAVAILABLE' | 'DISABLED' | 'UNKNOWN'

export interface DependencyProbeResult {
  status: DependencyStatus
  detail: string
}

export interface OutboxSummary {
  pending: number
  failed: number
  status: DependencyStatus
}

export interface DlqSummary {
  count: number
}

export interface AttemptSummary {
  statusCounts: Record<string, number>
  stuckProcessingCount: number
}

export interface Counters {
  transactionProcessed: number
  transactionSucceeded: number
  transactionFailed: number
  decisionsByType: Record<string, number>
  kafkaConsumed: number
  kafkaSucceeded: number
  kafkaDuplicate: number
  kafkaRetryable: number
  kafkaPermanent: number
  kafkaDeadLettered: number
  mlRequests: number
  mlSuccess: number
  mlFailure: number
  aiRequests: number
  aiSuccess: number
  aiFailure: number
}

export interface Latency {
  transactionMeanMs: number
  kafkaMeanMs: number
  mlMeanMs: number
  aiMeanMs: number
}

export interface OperationalSummary {
  generatedAt: string
  dependencies: Record<string, DependencyProbeResult>
  outbox: OutboxSummary
  dlq: DlqSummary
  attempts: AttemptSummary
  counters: Counters
  latencyMs: Latency
}

export interface AttemptDto {
  eventId: string
  transactionReference: string
  correlationId: string | null
  eventType: string
  status: string
  attemptCount: number
  lastError: string | null
  createdAt: string
  updatedAt: string
}

export interface DlqResponse {
  count: number
  recent: AttemptDto[]
}

export interface AttemptSummaryResponse {
  statusCounts: Record<string, number>
  stuckThresholdMinutes: number
  stuckCount: number
  stuck: AttemptDto[]
}

export interface OutboxRowDto {
  id: string
  aggregateType: string
  aggregateId: string
  eventType: string
  retryCount: number
  createdAt: string
}

export interface OutboxResponse {
  pending: number
  failed: number
  status: DependencyStatus
  oldestPendingCreatedAt: string | null
  oldestPendingAgeSeconds: number
  recentFailed: OutboxRowDto[]
}

export interface IntegrityCheck {
  id: string
  description: string
  issueCount: number
  healthy: boolean
}

export interface IntegrityResponse {
  generatedAt: string
  checks: IntegrityCheck[]
  healthyAll: boolean
}

export const operationsApi = {
  summary(): Promise<OperationalSummary> {
    return get<OperationalSummary>('/api/operations/summary')
  },

  dlq(): Promise<DlqResponse> {
    return get<DlqResponse>('/api/operations/dlq')
  },

  attempts(): Promise<AttemptSummaryResponse> {
    return get<AttemptSummaryResponse>('/api/operations/attempts')
  },

  outbox(): Promise<OutboxResponse> {
    return get<OutboxResponse>('/api/operations/outbox')
  },

  integrity(): Promise<IntegrityResponse> {
    return get<IntegrityResponse>('/api/operations/integrity')
  },
}
