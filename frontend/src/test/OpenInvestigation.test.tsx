import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OpenInvestigation } from '@/components/investigation/OpenInvestigation'
import { investigationsApi } from '@/api/investigations'
import { investigationMetadataFixture } from './fixtures'
import { renderWithRouter } from './render'

vi.mock('@/api/investigations', () => ({
  investigationsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    summary: vi.fn(),
    timeline: vi.fn(),
    addEvent: vi.fn(),
    evidence: vi.fn(),
    replay: vi.fn(),
  },
}))

const mockedInvestigations = vi.mocked(investigationsApi)

describe('OpenInvestigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('creates an investigation with the chosen priority and note', async () => {
    const user = userEvent.setup()
    const created = investigationMetadataFixture()
    mockedInvestigations.create.mockResolvedValue(created)
    const onCreated = vi.fn()

    renderWithRouter(
      <OpenInvestigation transactionReference="txn-demo-001" existing={[]} onCreated={onCreated} />,
    )

    await user.selectOptions(screen.getByLabelText('Priority'), 'HIGH')
    await user.type(screen.getByLabelText('Opening note'), 'Amount pattern needs review.')
    await user.click(screen.getByRole('button', { name: 'Open investigation' }))

    await waitFor(() => {
      expect(mockedInvestigations.create).toHaveBeenCalledWith({
        transactionReference: 'txn-demo-001',
        priority: 'HIGH',
        note: 'Amount pattern needs review.',
      })
    })
    expect(onCreated).toHaveBeenCalledWith(created)
    expect(await screen.findByText(/INV-000000001/)).toBeInTheDocument()
  })

  it('links to an active investigation instead of inviting a duplicate', () => {
    const active = investigationMetadataFixture({ id: 'uuid-active', investigationReference: 'INV-000000009' })
    renderWithRouter(
      <OpenInvestigation transactionReference="txn-demo-001" existing={[active]} onCreated={() => {}} />,
    )
    expect(screen.getByText('INV-000000009')).toBeInTheDocument()
    expect(screen.getByText('Open investigation')).toBeInTheDocument()
  })
})