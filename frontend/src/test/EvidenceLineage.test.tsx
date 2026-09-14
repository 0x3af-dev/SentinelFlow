import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EvidenceLineage } from '@/components/evidence/EvidenceLineage'
import { layoutLayers } from '@/lib/lineage'
import { replayFixture } from './fixtures'

describe('EvidenceLineage', () => {
  it('lays evidence into deterministic levels along the decision path', () => {
    const replay = replayFixture()
    const { levels } = layoutLayers(replay.evidence.nodes, replay.evidence.edges)
    expect(levels.length).toBeGreaterThan(0)
    // Transaction is a root; decision comes later in the flow.
    expect(levels[0]!.some((n) => n.nodeType === 'TRANSACTION')).toBe(true)
    expect(levels.at(-1)!.some((n) => n.nodeType === 'DECISION')).toBe(true)
  })

  it('renders node cards and marks the decision node on the path', () => {
    const replay = replayFixture()
    render(<EvidenceLineage nodes={replay.evidence.nodes} edges={replay.evidence.edges} />)
    expect(screen.getAllByText('TRANSACTION').length).toBeGreaterThan(0)
    expect(screen.getAllByText('DECISION').length).toBeGreaterThan(0)
    expect(screen.getAllByText('→ decision')).not.toHaveLength(0)
  })

  it('shows an explicit empty state when there is nothing recorded', () => {
    render(<EvidenceLineage nodes={[]} edges={[]} />)
    expect(screen.getByText('No evidence nodes were found for this transaction.')).toBeInTheDocument()
  })
})