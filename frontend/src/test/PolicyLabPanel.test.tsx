import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PolicyLabPanel } from '@/components/lab/PolicyLabPanel'
import { analyticsApi } from '@/api/analytics'
import { policySimulationFixture, replayFixture } from './fixtures'

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    simulatePolicy: vi.fn(),
    listSimulations: vi.fn(),
    counterfactualFeatures: vi.fn(),
    listCounterfactuals: vi.fn(),
    runCounterfactual: vi.fn(),
  },
}))

const mockedAnalytics = vi.mocked(analyticsApi)

describe('PolicyLabPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('labels itself as a simulation and never as a production change', () => {
    render(
      <PolicyLabPanel transactionReference="txn-demo-001" policy={replayFixture().policy} />,
    )
    expect(screen.getByText('Open lab')).toBeInTheDocument()
    expect(screen.getByText('Not a production policy change')).toBeInTheDocument()
  })

  it('validates 0 < review < block < 1 before submitting', async () => {
    const user = userEvent.setup()
    render(<PolicyLabPanel transactionReference="txn-demo-001" policy={replayFixture().policy} />)
    await user.click(screen.getByText('Open lab'))

    const review = screen.getByLabelText('Review threshold (percent)')
    const block = screen.getByLabelText('Block threshold (percent)')
    const simulate = screen.getByRole('button', { name: 'Simulate' })

    // Invalid ordering: review above block.
    await user.clear(review)
    await user.type(review, '80')
    await user.clear(block)
    await user.type(block, '75')
    expect(simulate).toBeDisabled()
    expect(
      screen.getByText(/0 < review < block < 1/),
    ).toBeInTheDocument()

    // Valid: review 75, block 90.
    await user.clear(review)
    await user.type(review, '75')
    await user.clear(block)
    await user.type(block, '90')
    expect(simulate).toBeEnabled()
  })

  it('sends the exact simulated thresholds and renders the simulated decision', async () => {
    const user = userEvent.setup()
    const replay = replayFixture()
    mockedAnalytics.simulatePolicy.mockResolvedValue(policySimulationFixture())

    render(<PolicyLabPanel transactionReference={replay.transactionReference} policy={replay.policy} />)
    await user.click(screen.getByText('Open lab'))

    const review = screen.getByLabelText('Review threshold (percent)')
    const block = screen.getByLabelText('Block threshold (percent)')
    await user.clear(review)
    await user.type(review, '75')
    await user.clear(block)
    await user.type(block, '90')
    await user.click(screen.getByRole('button', { name: 'Simulate' }))

    await waitFor(() => {
      expect(mockedAnalytics.simulatePolicy).toHaveBeenCalledWith({
        transactionReference: replay.transactionReference,
        policyName: 'fraud-policy',
        policyVersion: 'v1',
        reviewThreshold: 0.75,
        blockThreshold: 0.9,
      })
    })

    const result = await screen.findByTestId('policy-lab-result')
    expect(result).toHaveTextContent('ALLOW')
    expect(result).toHaveTextContent('More permissive')
    expect(result).toHaveTextContent('sim-1')
  })
})