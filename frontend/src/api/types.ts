/**
 * Type mirrors of the SentinelFlow backend DTOs (records). Field names and
 * shapes come from the actual Phase 4 controllers/DTOs — the frontend never
 * re-implements backend logic, it only consumes these contracts.
 */

export type Decision = 'ALLOW' | 'REVIEW' | 'BLOCK'

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type InvestigationStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED'

export interface ApiError {
  code: string
  message: string
  details: Record<string, unknown>
}

export interface TransactionOverview {
  transactionReference: string
  /** BigDecimal amount serialized as JSON number by the backend. */
  amount: number
  currency: string
  channel: string | null
  transactionType: string | null
  status: string
  transactionTimestamp: string
  createdAt: string
  decided: boolean
}

export interface TransactionInfo {
  status: string
  amount: number
  currency: string
  channel: string
  transactionType: string
  timestamp: string
}

export interface FeatureSnapshotInfo {
  id: string
  schemaVersion: string
  generatedAt: string
  features: Record<string, unknown>
}

export interface ModelInfo {
  name: string
  version: string
  algorithm: string
  featureSchemaVersion: string
}

export interface RiskScoreInfo {
  score: number
  prediction: string
  inferenceTimestamp: string
  inferenceLatencyMs: number | null
}

export interface RiskFactorInfo {
  factorType: string
  description: string
  severity: string
  source: string
}

export interface RuleInfo {
  ruleId: string
  ruleVersion: string
  severity: string
  description: string
  observedValues: Record<string, unknown>
}

export interface PolicyInfo {
  name: string
  version: string
  reviewThreshold: number | null
  blockThreshold: number | null
  configuration: Record<string, unknown>
}

export interface DecisionInfo {
  finalDecision: Decision
  reason: string
  decisionTimestamp: string
}

export interface DisagreementInfo {
  modelLevel: 'HIGH' | 'LOW'
  ruleLevel: 'HIGH' | 'LOW'
  category: string
  summary: string
}

export interface EvidenceNodeDto {
  id: string
  nodeType: string
  sourceType: string
  entityType: string
  entityId: string
  observedAt: string
  value: Record<string, unknown>
}

export interface EvidenceEdgeDto {
  id: string
  sourceNodeId: string
  targetNodeId: string
  relationshipType: string
}

export interface EvidenceInfo {
  nodeCount: number
  edgeCount: number
  nodes: EvidenceNodeDto[]
  edges: EvidenceEdgeDto[]
}

export interface DecisionReplay {
  transactionReference: string
  transaction: TransactionInfo
  featureSnapshot: FeatureSnapshotInfo
  model: ModelInfo
  riskScore: RiskScoreInfo
  riskFactors: RiskFactorInfo[]
  triggeredRules: RuleInfo[]
  policy: PolicyInfo
  decision: DecisionInfo
  disagreement: DisagreementInfo
  evidence: EvidenceInfo
}

export interface PolicySimulationResponse {
  simulationId: string
  transactionReference: string
  policyName: string
  policyVersion: string
  reviewThreshold: number
  blockThreshold: number
  baseRiskScore: number
  baseModelName: string
  baseModelVersion: string
  actualDecision: Decision
  actualReason: string
  simulatedDecision: Decision
  simulatedReason: string
  decisionChanged: boolean
  changeType: string
  createdAt: string
}

export interface FeatureModificationInfo {
  feature: string
  originalValue: unknown
  modifiedValue: unknown
}

export interface CounterfactualResponse {
  analysisId: string
  transactionReference: string
  featureSnapshotId: string
  featureSchemaVersion: string
  modelName: string
  modelVersion: string
  appliedModifications: FeatureModificationInfo[]
  originalRiskScore: number
  hypotheticalRiskScore: number
  scoreDelta: number
  originalDecision: Decision
  hypotheticalDecision: Decision
  decisionChanged: boolean
  changeType: string
  createdAt: string
  disclaimer: string
}

export interface InvestigationMetadata {
  id: string
  investigationReference: string
  transactionReference: string
  status: InvestigationStatus
  priority: Priority
  assignedTo: string | null
  openedAt: string
  updatedAt: string
  resolvedAt: string | null
  resolution: string | null
  resolutionNotes: string | null
  eventCount: number
}

export interface TimelineEntry {
  eventId: string
  eventType: string
  actorType: string
  actorReference: string
  eventTimestamp: string
  payload: Record<string, unknown>
}

export interface InvestigationSummary {
  transactionReference: string
  transaction: TransactionInfo
  riskScore: RiskScoreInfo
  decision: DecisionInfo
  riskFactors: RiskFactorInfo[]
  triggeredRules: RuleInfo[]
  disagreement: DisagreementInfo
  evidenceNodeCount: number
  evidenceEdgeCount: number
  policySimulations: PolicySimulationResponse[]
  counterfactuals: CounterfactualResponse[]
  investigation: InvestigationMetadata
}

export interface EvidenceGraph {
  nodes: EvidenceNodeDto[]
  edges: EvidenceEdgeDto[]
}

/** Editable counterfactual feature exposed by the backend registry. */
export interface CounterfactualFeatureSpec {
  name: string
  description: string
  min: number
  max: number
  integral: boolean
}

/** Request bodies (POST). */
export interface PolicySimulationRequest {
  transactionReference: string
  policyName: string
  policyVersion: string
  reviewThreshold: number
  blockThreshold: number
  requestedBy?: string
  investigationId?: string
}

export interface CounterfactualModification {
  feature: string
  value: number
}

export interface CounterfactualRequest {
  transactionReference: string
  modifications: CounterfactualModification[]
  requestedBy?: string
  investigationId?: string
}

export interface CreateInvestigationRequest {
  transactionReference: string
  priority?: Priority
  assignedTo?: string
  note?: string
}

export interface AddInvestigationEventRequest {
  eventType: string
  actorType: 'ANALYST' | 'SYSTEM'
  actorReference?: string
  payload: Record<string, unknown>
}

export interface PipelineResult {
  transactionReference: string
  status: string
  featureSnapshotReference: string
  modelVersion: string
  riskScore: number | null
  riskFactors: Array<Record<string, unknown>>
  decision: string
  decisionReason: string
  policyVersion: string
  decisionTimestamp: string
  evidenceId: string
  pipelineStartTimestamp: string
  pipelineEndTimestamp: string
}

/**
 * AI investigator (Phase 6). Every factual claim that references SentinelFlow
 * data carries evidenceIds that resolve to persisted evidence nodes; the
 * backend validates this before anything is returned here.
 */
export type InvestigationRequestType =
  | 'WHY_FLAGGED'
  | 'SUMMARIZE'
  | 'RISK_FACTORS'
  | 'CONFLICTS'
  | 'BEHAVIORAL'
  | 'NEXT_EVIDENCE'
  | 'FREE_FORM'

export interface InvestigationExplanationRequest {
  requestType: InvestigationRequestType
  /** Only allowed for FREE_FORM; the backend rejects it otherwise. */
  freeFormQuestion?: string | null
}

export interface InvestigationExplanationObservation {
  statement: string
  evidenceIds: string[]
}

export interface InvestigationExplanationRiskAssessment {
  recordedRiskScore: number
  recordedDecision: string
  decisionPolicy: string | null
  explanation: string
  evidenceIds: string[]
}

export interface InvestigationExplanationFinding {
  statement: string
  evidenceIds: string[]
}

export interface InvestigationExplanationRuleFinding {
  ruleId: string
  statement: string
  outcome: string
  evidenceIds: string[]
}

export interface InvestigationExplanationEvidenceConflict {
  description: string
  evidenceIds: string[]
}

export interface InvestigationExplanationCounterfactual {
  analysisId: string
  feature: string
  recordedValue: unknown
  hypotheticalValue: unknown
  recordedScore: number
  hypotheticalScore: number
  hypotheticalDecision: string
  statement: string
  disclaimer: string
  evidenceIds: string[]
}

export interface InvestigationExplanationSimulation {
  simulationId: string
  hypotheticalPolicyName: string
  hypotheticalPolicyVersion: string
  simulatedDecision: string
  statement: string
  disclaimer: string
  evidenceIds: string[]
}

export interface InvestigationExplanationUncertainty {
  statement: string
  reason: string
}

export interface InvestigationExplanationRecommendedEvidence {
  request: string
  rationale: string
}

export interface InvestigationExplanationEvidenceReference {
  evidenceId: string
  sourceType: string
  sourceId: string
  description: string
}

export interface InvestigationExplanationModelMetadata {
  provider: string
  model: string
  toolCallCount: number
  correlationId: string
}

export interface InvestigationExplanation {
  investigationId: string
  transactionReference: string
  requestType: InvestigationRequestType
  summary: string
  observations: InvestigationExplanationObservation[]
  riskAssessment: InvestigationExplanationRiskAssessment
  modelFindings: InvestigationExplanationFinding[]
  ruleFindings: InvestigationExplanationRuleFinding[]
  behavioralFindings: InvestigationExplanationFinding[]
  evidenceConflicts: InvestigationExplanationEvidenceConflict[]
  simulations: InvestigationExplanationSimulation[]
  counterfactuals: InvestigationExplanationCounterfactual[]
  uncertainty: InvestigationExplanationUncertainty[]
  recommendedNextEvidence: InvestigationExplanationRecommendedEvidence[]
  evidenceReferences: InvestigationExplanationEvidenceReference[]
  generatedAt: string
  modelMetadata: InvestigationExplanationModelMetadata
}

/** Immutable audit trail of one AI investigation run. */
export interface AiInvestigationRun {
  id: string
  investigationId: string
  requestType: InvestigationRequestType
  freeFormQuestion: string | null
  status: 'SUCCEEDED' | 'FAILED'
  provider: string | null
  model: string | null
  toolCallCount: number
  latencyMs: number | null
  correlationId: string | null
  response: InvestigationExplanation | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
}