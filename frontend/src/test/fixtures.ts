import type {
  CounterfactualResponse,
  DecisionReplay,
  Decision,
  InvestigationMetadata,
  InvestigationSummary,
  PolicySimulationResponse,
  TimelineEntry,
  TransactionOverview,
} from '@/api/types'

/**
 * Contract fixtures mirroring the real backend JSON shape for the demo
 * narrative (see spec §71): txn-demo-001 – REVIEW → policy-lab ALLOW →
 * counterfactual BLOCK. Used by component tests; never shipped as UI data.
 */

export const overviewFixture = (overrides: Partial<TransactionOverview> = {}): TransactionOverview => ({
  transactionReference: 'txn-demo-001',
  amount: 12000,
  currency: 'INR',
  channel: 'ONLINE',
  transactionType: 'PURCHASE',
  status: 'COMPLETED',
  transactionTimestamp: '2026-09-14T10:00:00Z',
  createdAt: '2026-09-14T10:00:01Z',
  decided: true,
  ...overrides,
})

export const replayFixture = (overrides: Partial<DecisionReplay> = {}): DecisionReplay => ({
  transactionReference: 'txn-demo-001',
  transaction: {
    status: 'COMPLETED',
    amount: 12000,
    currency: 'INR',
    channel: 'ONLINE',
    transactionType: 'PURCHASE',
    timestamp: '2026-09-14T10:00:00Z',
  },
  featureSnapshot: {
    id: 'fs-txn-demo-001',
    schemaVersion: 'fs-v1',
    generatedAt: '2026-09-14T10:00:02Z',
    features: { transaction_amount: 12000, sender_history_count: 12, device_fingerprint_score: 0.3 },
  },
  model: { name: 'risk-model', version: 'v1', algorithm: 'gradient-boosting', featureSchemaVersion: 'fs-v1' },
  riskScore: {
    score: 0.6,
    prediction: 'MEDIUM',
    inferenceTimestamp: '2026-09-14T10:00:03Z',
    inferenceLatencyMs: 12,
  },
  riskFactors: [
    {
      factorType: 'MODEL_ELEVATED_RISK',
      description: 'elevated risk',
      severity: 'MEDIUM',
      source: 'MODEL_OUTPUT',
    },
  ],
  triggeredRules: [
    {
      ruleId: 'large_amount_review',
      ruleVersion: '1',
      severity: 'MEDIUM',
      description: 'Large amount transaction flagged for review.',
      observedValues: { amount: 12000 },
    },
  ],
  policy: { name: 'fraud-policy', version: 'v1', reviewThreshold: 0.5, blockThreshold: 0.85, configuration: {} },
  decision: {
    finalDecision: 'REVIEW',
    reason: 'Risk score 0.60 falls within review threshold range [0.50, 0.85]',
    decisionTimestamp: '2026-09-14T10:00:04Z',
  },
  disagreement: {
    modelLevel: 'HIGH',
    ruleLevel: 'HIGH',
    category: 'ML_HIGH_RULE_HIGH',
    summary: 'Model and rule layers both indicate attention.',
  },
  evidence: {
    nodeCount: 2,
    edgeCount: 1,
    nodes: [
      {
        id: 'node-1',
        nodeType: 'TRANSACTION',
        sourceType: 'TRANSACTION_RECORD',
        entityType: 'TRANSACTION',
        entityId: 'txn-demo-001',
        observedAt: '2026-09-14T10:00:02Z',
        value: { amount: 12000 },
      },
      {
        id: 'node-2',
        nodeType: 'DECISION',
        sourceType: 'POLICY_ENGINE',
        entityType: 'DECISION_RECORD',
        entityId: 'dec-1',
        observedAt: '2026-09-14T10:00:04Z',
        value: { finalDecision: 'REVIEW' },
      },
    ],
    edges: [
      {
        id: 'edge-1',
        sourceNodeId: 'node-1',
        targetNodeId: 'node-2',
        relationshipType: 'PRODUCED',
      },
    ],
  },
  ...overrides,
})

export const policySimulationFixture = (overrides: Partial<PolicySimulationResponse> = {}): PolicySimulationResponse => ({
  simulationId: 'sim-1',
  transactionReference: 'txn-demo-001',
  policyName: 'fraud-policy',
  policyVersion: 'sim-v1',
  reviewThreshold: 0.75,
  blockThreshold: 0.9,
  baseRiskScore: 0.6,
  baseModelName: 'risk-model',
  baseModelVersion: 'v1',
  actualDecision: 'REVIEW',
  actualReason: 'Risk score 0.60 falls within review threshold range [0.50, 0.85]',
  simulatedDecision: 'ALLOW',
  simulatedReason: 'Risk score 0.60 is below review threshold 0.75',
  decisionChanged: true,
  changeType: 'MORE_PERMISSIVE',
  createdAt: '2026-09-14T10:10:00Z',
  ...overrides,
})

export const counterfactualFixture = (overrides: Partial<CounterfactualResponse> = {}): CounterfactualResponse => ({
  analysisId: 'cf-1',
  transactionReference: 'txn-demo-001',
  featureSnapshotId: 'fs-txn-demo-001',
  featureSchemaVersion: 'fs-v1',
  modelName: 'risk-model',
  modelVersion: 'v1',
  appliedModifications: [{ feature: 'transaction_amount', originalValue: 12000, modifiedValue: 60000 }],
  originalRiskScore: 0.6,
  hypotheticalRiskScore: 0.9,
  scoreDelta: 0.3,
  originalDecision: 'REVIEW',
  hypotheticalDecision: 'BLOCK',
  decisionChanged: true,
  changeType: 'MORE_RESTRICTIVE',
  createdAt: '2026-09-14T10:12:00Z',
  disclaimer:
    'Hypothetical model analysis. This does not modify the production decision.',
  ...overrides,
})

export const investigationMetadataFixture = (
  overrides: Partial<InvestigationMetadata> = {},
): InvestigationMetadata => ({
  id: '00000000-0000-0000-0000-000000000001',
  investigationReference: 'INV-000000001',
  transactionReference: 'txn-demo-001',
  status: 'OPEN',
  priority: 'HIGH',
  assignedTo: 'analyst-1',
  openedAt: '2026-09-14T10:15:00Z',
  updatedAt: '2026-09-14T10:15:00Z',
  resolvedAt: null,
  resolution: null,
  resolutionNotes: null,
  eventCount: 1,
  ...overrides,
})

export const summaryFixture = (overrides: Partial<InvestigationSummary> = {}): InvestigationSummary => {
  const replay = replayFixture()
  return {
    transactionReference: replay.transactionReference,
    transaction: replay.transaction,
    riskScore: replay.riskScore,
    decision: replay.decision,
    riskFactors: replay.riskFactors,
    triggeredRules: replay.triggeredRules,
    disagreement: replay.disagreement,
    evidenceNodeCount: replay.evidence.nodeCount,
    evidenceEdgeCount: replay.evidence.edgeCount,
    policySimulations: [policySimulationFixture()],
    counterfactuals: [counterfactualFixture()],
    investigation: investigationMetadataFixture(),
    ...overrides,
  }
}

export const timelineEventFixture = (overrides: Partial<TimelineEntry> = {}): TimelineEntry => ({
  eventId: 'evt-1',
  eventType: 'NOTE_ADDED',
  actorType: 'ANALYST',
  actorReference: 'analyst-1',
  eventTimestamp: '2026-09-14T10:20:00Z',
  payload: { note: 'Amount pattern consistent with browser fraud reports.' },
  ...overrides,
})

export const decisions: Decision[] = ['ALLOW', 'REVIEW', 'BLOCK']