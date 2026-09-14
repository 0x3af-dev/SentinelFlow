import { get, post } from './client'
import type {
  CounterfactualFeatureSpec,
  CounterfactualRequest,
  CounterfactualResponse,
  PolicySimulationRequest,
  PolicySimulationResponse,
} from './types'

const encode = (ref: string): string => encodeURIComponent(ref)

export const analyticsApi = {
  /** Editable counterfactual features supported by the backend registry. */
  counterfactualFeatures(): Promise<CounterfactualFeatureSpec[]> {
    return get<CounterfactualFeatureSpec[]>('/api/counterfactuals/features')
  },

  simulatePolicy(body: PolicySimulationRequest): Promise<PolicySimulationResponse> {
    return post<PolicySimulationResponse>('/api/policy-lab/simulate', body)
  },

  listSimulations(reference: string): Promise<PolicySimulationResponse[]> {
    return get<PolicySimulationResponse[]>(`/api/policy-lab/transactions/${encode(reference)}/simulations`)
  },

  runCounterfactual(body: CounterfactualRequest): Promise<CounterfactualResponse> {
    return post<CounterfactualResponse>('/api/counterfactuals', body)
  },

  listCounterfactuals(reference: string): Promise<CounterfactualResponse[]> {
    return get<CounterfactualResponse[]>(`/api/counterfactuals/transactions/${encode(reference)}`)
  },
}