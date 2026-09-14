import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SystemHealthPage } from '@/pages/SystemHealthPage'
import * as ops from '@/api/operations'

vi.mock('@/api/operations')

const SUMMARY: ops.OperationalSummary = {
  generatedAt: '2026-09-14T10:00:00Z',
  dependencies: {
    postgres: { status: 'HEALTHY', detail: 'SELECT 1 ok' },
    ml: { status: 'UNAVAILABLE', detail: 'connection refused' },
    ai: { status: 'DISABLED', detail: 'not configured' },
  },
  outbox: { pending: 3, failed: 1, status: 'HEALTHY' },
  dlq: { count: 0 },
  attempts: { statusCounts: { PENDING: 5, PUBLISHED: 12 }, stuckProcessingCount: 0 },
  counters: {
    transactionProcessed: 100,
    transactionSucceeded: 95,
    transactionFailed: 5,
    decisionsByType: { ALLOW: 80, REVIEW: 15, BLOCK: 5 },
    kafkaConsumed: 50,
    kafkaSucceeded: 48,
    kafkaDuplicate: 2,
    kafkaRetryable: 0,
    kafkaPermanent: 0,
    kafkaDeadLettered: 0,
    mlRequests: 95,
    mlSuccess: 90,
    mlFailure: 5,
    aiRequests: 0,
    aiSuccess: 0,
    aiFailure: 0,
  },
  latencyMs: { transactionMeanMs: 192, kafkaMeanMs: 45, mlMeanMs: 120, aiMeanMs: 0 },
}

const INTEGRITY: ops.IntegrityResponse = {
  generatedAt: '2026-09-14T10:00:00Z',
  healthyAll: true,
  checks: [
    { id: 'orphaned_evidence_edges', description: 'evidence edges referencing missing nodes', issueCount: 0, healthy: true },
    { id: 'orphaned_risk_scores', description: 'risk scores referencing missing feature snapshots', issueCount: 0, healthy: true },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/system-health']}>
      <SystemHealthPage />
    </MemoryRouter>,
  )
}

describe('SystemHealthPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders loading state then health data', async () => {
    vi.mocked(ops.operationsApi.summary).mockResolvedValue(SUMMARY)
    vi.mocked(ops.operationsApi.integrity).mockResolvedValue(INTEGRITY)

    renderPage()
    expect(screen.getByText(/Loading system health/)).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('System Health')).toBeInTheDocument()
    })

    expect(screen.getByText('postgres')).toBeInTheDocument()
    expect(screen.getByText('ml')).toBeInTheDocument()
    expect(screen.getByText('HEALTHY')).toBeInTheDocument()
    expect(screen.getByText('UNAVAILABLE')).toBeInTheDocument()
    expect(screen.getByText('DISABLED')).toBeInTheDocument()
  })

  it('renders counter metrics', async () => {
    vi.mocked(ops.operationsApi.summary).mockResolvedValue(SUMMARY)
    vi.mocked(ops.operationsApi.integrity).mockResolvedValue(INTEGRITY)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Transaction Metrics')).toBeInTheDocument()
    })

    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByText('Transaction Metrics')).toBeInTheDocument()
  })

  it('renders data integrity section', async () => {
    vi.mocked(ops.operationsApi.summary).mockResolvedValue(SUMMARY)
    vi.mocked(ops.operationsApi.integrity).mockResolvedValue(INTEGRITY)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Data Integrity')).toBeInTheDocument()
    })

    expect(screen.getByText('ALL HEALTHY')).toBeInTheDocument()
  })

  it('shows error panel on failure', async () => {
    vi.mocked(ops.operationsApi.summary).mockRejectedValue(new Error('network'))

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })
})
