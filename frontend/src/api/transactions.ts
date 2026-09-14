import { get, post } from './client'
import type { DecisionReplay, PipelineResult, TransactionOverview } from './types'

const encode = (ref: string): string => encodeURIComponent(ref)

export const transactionsApi = {
  overview(ref: string): Promise<TransactionOverview> {
    return get<TransactionOverview>(`/api/transactions/${encode(ref)}`)
  },

  decisionReplay(ref: string): Promise<DecisionReplay> {
    return get<DecisionReplay>(`/api/transactions/${encode(ref)}/decision-replay`)
  },

  /** Runs the transaction through the intelligence pipeline (demo/dev narrative). */
  process(ref: string): Promise<PipelineResult> {
    return post<PipelineResult>(`/api/transactions/${encode(ref)}/process`, {})
  },
}