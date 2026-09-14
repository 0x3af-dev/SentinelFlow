import { get, post } from './client'
import type {
  AddInvestigationEventRequest,
  AiInvestigationRun,
  CreateInvestigationRequest,
  DecisionReplay,
  EvidenceGraph,
  InvestigationExplanation,
  InvestigationExplanationRequest,
  InvestigationMetadata,
  InvestigationSummary,
  TimelineEntry,
} from './types'

export const investigationsApi = {
  list(transactionReference?: string): Promise<InvestigationMetadata[]> {
    const qs = transactionReference
      ? `?transactionReference=${encodeURIComponent(transactionReference)}`
      : ''
    return get<InvestigationMetadata[]>(`/api/investigations${qs}`)
  },

  get(id: string): Promise<InvestigationMetadata> {
    return get<InvestigationMetadata>(`/api/investigations/${encodeURIComponent(id)}`)
  },

  create(body: CreateInvestigationRequest): Promise<InvestigationMetadata> {
    return post<InvestigationMetadata>('/api/investigations', body)
  },

  summary(id: string): Promise<InvestigationSummary> {
    return get<InvestigationSummary>(`/api/investigations/${encodeURIComponent(id)}/summary`)
  },

  timeline(id: string): Promise<TimelineEntry[]> {
    return get<TimelineEntry[]>(`/api/investigations/${encodeURIComponent(id)}/timeline`)
  },

  addEvent(id: string, body: AddInvestigationEventRequest): Promise<TimelineEntry> {
    return post<TimelineEntry>(`/api/investigations/${encodeURIComponent(id)}/events`, body)
  },

  evidence(id: string): Promise<EvidenceGraph> {
    return get<EvidenceGraph>(`/api/investigations/${encodeURIComponent(id)}/evidence`)
  },

  replay(id: string): Promise<DecisionReplay> {
    return get<DecisionReplay>(`/api/investigations/${encodeURIComponent(id)}/decision-replay`)
  },

  explain(id: string, body: InvestigationExplanationRequest): Promise<InvestigationExplanation> {
    return post<InvestigationExplanation>(`/api/investigations/${encodeURIComponent(id)}/explanations`, body)
  },

  explanationRuns(id: string): Promise<AiInvestigationRun[]> {
    return get<AiInvestigationRun[]>(`/api/investigations/${encodeURIComponent(id)}/explanations/runs`)
  },
}