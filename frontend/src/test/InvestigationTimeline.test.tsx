import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { InvestigationTimeline } from '@/components/timeline/InvestigationTimeline'
import { counterfactualFixture, policySimulationFixture, replayFixture, timelineEventFixture } from './fixtures'

describe('InvestigationTimeline', () => {
  it('renders merged artifacts and events in strict chronological order', () => {
    const replay = replayFixture()
    const sim = policySimulationFixture()
    const count = counterfactualFixture()
    const event = timelineEventFixture({ eventTimestamp: '2026-09-14T10:20:00Z' })
    // Feed events out of order to prove ordering is by the recorded timestamp.
    render(
      <InvestigationTimeline
        events={[event]}
        replay={replay}
        simulations={[sim]}
        counterfactuals={[count]}
      />,
    )

    const rows = screen.getAllByRole('listitem')
    const text = rows.map((row) => row.textContent ?? '')

    const scoreIndex = text.findIndex((t) => t.includes('Risk scored 0.60'))
    const decisionIndex = text.findIndex((t) => t.includes('Policy decision recorded'))
    const simIndex = text.findIndex((t) => t.includes('Policy simulated'))
    const cfIndex = text.findIndex((t) => t.includes('Hypothetical analysis'))
    const noteIndex = text.findIndex((t) => t.includes('Note added'))

    expect(scoreIndex).toBeGreaterThanOrEqual(0)
    expect(decisionIndex).toBeGreaterThan(scoreIndex)
    expect(simIndex).toBeGreaterThan(decisionIndex)
    expect(cfIndex).toBeGreaterThan(simIndex)
    expect(noteIndex).toBeGreaterThan(cfIndex)
  })

  it('shows an explicit empty state when nothing is recorded yet', () => {
    render(<InvestigationTimeline events={[]} replay={null} simulations={[]} counterfactuals={[]} />)
    expect(screen.getByText('No timeline entries recorded yet.')).toBeInTheDocument()
  })
})