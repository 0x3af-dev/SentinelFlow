import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RiskScorePanel } from '@/components/risk/RiskScorePanel'
import { replayFixture } from './fixtures'

describe('RiskScorePanel', () => {
  it('shows the backend risk score and the contributing factors', () => {
    const replay = replayFixture()
    render(
      <RiskScorePanel
        riskScore={replay.riskScore}
        model={replay.model}
        featureSnapshot={replay.featureSnapshot}
        riskFactors={replay.riskFactors}
      />,
    )
    expect(screen.getByText('0.60', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('MODEL_ELEVATED_RISK')).toBeInTheDocument()
    expect(screen.getAllByText('MEDIUM').length).toBeGreaterThan(0)
    expect(screen.getByText(/risk-model\/v1/)).toBeInTheDocument()
    expect(screen.getAllByText(/fs-txn-demo-001/).length).toBeGreaterThan(0)
  })

  it('shows an explicit empty state when no factors were recorded', () => {
    const replay = replayFixture({ riskFactors: [] })
    render(
      <RiskScorePanel
        riskScore={replay.riskScore}
        model={replay.model}
        featureSnapshot={replay.featureSnapshot}
        riskFactors={replay.riskFactors}
      />,
    )
    expect(
      screen.getByText('No risk factors were recorded for this decision.'),
    ).toBeInTheDocument()
    // Absence of factors must not be presented as a safety claim.
    expect(screen.getByText('Absence of factors is not a safety claim.')).toBeInTheDocument()
  })
})