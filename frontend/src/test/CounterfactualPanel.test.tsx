import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CounterfactualPanel } from '@/components/lab/CounterfactualPanel'
import { analyticsApi } from '@/api/analytics'
import { counterfactualFixture, replayFixture } from './fixtures'

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

const FEATURES = [
  { name: 'transaction_amount', description: 'Transaction amount (INR)', min: 0, max: 10000000, integral: true },
  { name: 'sender_history_count', description: 'Sender event count', min: 1, max: 1000, integral: true },
]

describe('CounterfactualPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedAnalytics.counterfactualFeatures.mockResolvedValue(FEATURES)
  })

  it('lists only backend-supported features and pre-fills recorded values', async () => {
    const replay = replayFixture()
    render(
      <CounterfactualPanel
        transactionReference={replay.transactionReference}
        recordedFeatures={replay.featureSnapshot.features}
      />,
    )
    expect(await screen.findByText('transaction_amount')).toBeInTheDocument()
    expect(screen.getByText('sender_history_count')).toBeInTheDocument()
    expect(screen.getByLabelText('transaction_amount hypothetical value')).toHaveValue(12000)
  })

  it('submits only modified features and renders the hypothetical result with the disclaimer', async () => {
    const user = userEvent.setup()
    const replay = replayFixture()
    mockedAnalytics.runCounterfactual.mockResolvedValue(counterfactualFixture())

    render(
      <CounterfactualPanel
        transactionReference={replay.transactionReference}
        recordedFeatures={replay.featureSnapshot.features}
      />,
    )
    const amountInput = await screen.findByLabelText('transaction_amount hypothetical value')
    await user.clear(amountInput)
    await user.type(amountInput, '60000')
    await user.click(screen.getByRole('button', { name: 'Run hypothetical' }))

    await waitFor(() => {
      expect(mockedAnalytics.runCounterfactual).toHaveBeenCalledWith({
        transactionReference: replay.transactionReference,
        modifications: [{ feature: 'transaction_amount', value: 60000 }],
      })
    })

    const result = await screen.findByTestId('counterfactual-result')
    // Recorded (original) vs hypothetical, as computed by the backend.
    expect(result).toHaveTextContent('0.60')
    expect(result).toHaveTextContent('0.90')
    expect(result).toHaveTextContent('REVIEW')
    expect(result).toHaveTextContent('BLOCK')
    expect(result).toHaveTextContent('+0.30')
    expect(result).toHaveTextContent(
      'Hypothetical model analysis. This does not modify the production decision.',
    )
  })

  it('disables the run button when nothing is modified', async () => {
    const user = userEvent.setup()
    const replay = replayFixture()
    render(
      <CounterfactualPanel
        transactionReference={replay.transactionReference}
        recordedFeatures={replay.featureSnapshot.features}
      />,
    )
    const amountInput = await screen.findByLabelText('transaction_amount hypothetical value')
    expect(amountInput).toHaveValue(12000)
    expect(screen.getByRole('button', { name: 'Run hypothetical' })).toBeDisabled()
    // Reset keeps recorded values intact.
    await user.click(screen.getByRole('button', { name: 'Reset to recorded' }))
    expect(amountInput).toHaveValue(12000)
  })
})