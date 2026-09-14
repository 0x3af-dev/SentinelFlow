import { describe, expect, it } from 'vitest'
import { ApiNetworkError, ApiRequestError, describeError } from '@/api/client'

describe('describeError', () => {
  it('maps 404 to a not-found headline', () => {
    const error = new ApiRequestError(404, 'NOT_FOUND', 'Transaction not found: nope', {})
    const { title, detail } = describeError(error)
    expect(title).toBe('Not found')
    expect(detail).toContain('Transaction not found')
  })

  it('maps 400 to an invalid-request headline and keeps the backend message', () => {
    const error = new ApiRequestError(400, 'VALIDATION_ERROR', 'review threshold must be > block threshold', {
      field: 'reviewThreshold',
    })
    const { title, detail } = describeError(error)
    expect(title).toBe('Invalid request')
    expect(detail).toContain('review threshold')
  })

  it('maps 503 to a model-service-unavailable message without leaking stack traces', () => {
    const error = new ApiRequestError(503, 'ML_UNAVAILABLE', 'upstream refused', {})
    const { title, detail } = describeError(error)
    expect(title).toBe('Model service unavailable')
    expect(detail).not.toContain('stack')
  })

  it('maps network failures to backend-unreachable', () => {
    const { title } = describeError(new ApiNetworkError(new TypeError('fetch failed')))
    expect(title).toBe('Backend unreachable')
  })

  it('never renders raw unknown values', () => {
    const { title, detail } = describeError({ weird: true })
    expect(title).toBe('Unexpected error')
    expect(detail).toBe('Unknown error')
  })
})