import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PolicyPanel } from '@/components/policy/PolicyPanel'
import { replayFixture } from './fixtures'

describe('PolicyPanel', () => {
  it('renders reviews/blocks thresholds and the recorded score position from backend values', () => {
    const replay = replayFixture()
    render(
      <PolicyPanel policy={replay.policy} riskScore={replay.riskScore} decision={replay.decision} />,
    )
    expect(screen.getByText(/review 0\.50 · block 0\.85/)).toBeInTheDocument()
    expect(screen.getAllByText('REVIEW').length).toBeGreaterThan(0)
    expect(screen.getAllByText('BLOCK').length).toBeGreaterThan(0)
    // The score marker reflects the backend risk score, not a local re-computation.
    expect(screen.getAllByText('0.60')).not.toHaveLength(0)
  })

  it('is robust when thresholds are absent (no segmentation implied)', () => {
    const replay = replayFixture({
      policy: { name: 'fraud-policy', version: 'v1', reviewThreshold: null, blockThreshold: null, configuration: {} },
    })
    render(
      <PolicyPanel policy={replay.policy} riskScore={replay.riskScore} decision={replay.decision} />,
    )
    expect(screen.getByText(/review — · block —/)).toBeInTheDocument()
  })
})