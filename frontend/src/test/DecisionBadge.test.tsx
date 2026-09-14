import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DecisionBadge } from '@/components/ui/DecisionBadge'
import { decisionDescription } from '@/lib/severity'

describe('DecisionBadge', () => {
  it('renders the value verbatim as received from the API (contract: REVIEW shows REVIEW)', () => {
    render(<DecisionBadge decision="REVIEW" />)
    expect(screen.getByText('REVIEW')).toBeInTheDocument()
  })

  it('renders ALLOW and BLOCK distinctly', () => {
    const { unmount } = render(<DecisionBadge decision="ALLOW" />)
    expect(screen.getByText('ALLOW')).toBeInTheDocument()
    unmount()
    render(<DecisionBadge decision="BLOCK" />)
    expect(screen.getByText('BLOCK')).toBeInTheDocument()
  })

  it.each(['ALLOW', 'REVIEW', 'BLOCK'] as const)('explains %s on hover (accessibility)', (decision) => {
    render(<DecisionBadge decision={decision} />)
    expect(screen.getByText(decision)).toHaveAttribute('title', decisionDescription[decision])
  })
})