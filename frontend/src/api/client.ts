import type { ApiError } from './types'
import { clearSession, getToken } from '@/auth/session'

/**
 * Structured API errors. The backend returns `{ code, message, details }` and
 * never leaks stack traces; this client maps transport failures to the same
 * envelope so the UI has exactly one error shape to render.
 */
export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, unknown>

  constructor(status: number, code: string, message: string, details: Record<string, unknown>) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.details = details
  }
}

export class ApiNetworkError extends Error {
  override readonly cause?: unknown

  constructor(cause?: unknown) {
    super('Network error: the backend is unreachable.')
    this.name = 'ApiNetworkError'
    this.cause = cause
  }
}

/** Human-friendly headline mapping for the most common codes. */
export function describeError(error: unknown): { title: string; detail: string } {
  if (error instanceof ApiRequestError) {
    if (error.code === 'AI_UNAVAILABLE') {
      return {
        title: 'AI investigator unavailable',
        detail:
          'The AI investigation capability is disabled or its provider could not be reached. No explanation was produced, and the persisted decision is unchanged.',
      }
    }
    if (error.code === 'AI_RESPONSE_INVALID') {
      return {
        title: 'AI response rejected',
        detail:
          'The model did not answer within the evidence rules and the response was rejected before anything could be shown. Retry, or ask a more targeted question.',
      }
    }
    if (error.status === 404) return { title: 'Not found', detail: error.message }
    if (error.status === 400) return { title: 'Invalid request', detail: error.message }
    if (error.status === 502) return { title: 'Upstream service error', detail: error.message }
    if (error.status === 503) {
      return {
        title: 'Model service unavailable',
        detail:
          'The ML risk service could not be reached. This analysis cannot run right now — try again when the service is healthy.',
      }
    }
    if (error.status === 500) return { title: 'Internal error', detail: error.message }
    return { title: 'Request failed', detail: error.message }
  }
  if (error instanceof ApiNetworkError) {
    return { title: 'Backend unreachable', detail: error.message }
  }
  return { title: 'Unexpected error', detail: error instanceof Error ? error.message : 'Unknown error' }
}

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

async function parseError(response: Response): Promise<ApiRequestError> {
  let envelope: ApiError | undefined
  try {
    const body = (await response.json()) as Partial<ApiError>
    if (typeof body.message === 'string') envelope = body as ApiError
  } catch {
    // non-JSON error body; fall through to generic
  }
  const status = response.status
  const code = envelope?.code ?? 'HTTP_' + status
  const message =
    envelope?.message ?? (status === 404 ? 'Resource not found' : `Request failed with status ${status}`)
  return new ApiRequestError(status, code, message, envelope?.details ?? {})
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body != null) headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  } catch (cause) {
    throw new ApiNetworkError(cause)
  }
  if (!response.ok) {
    if (response.status === 401) clearSession()
    throw await parseError(response)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' })
}

export function post<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: JSON.stringify(body) })
}