import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AiInvestigationPanel } from '@/components/ai/AiInvestigationPanel'
import { investigationsApi } from '@/api/investigations'
import { ApiRequestError } from '@/api/client'
import { aiInvestigationRunFixture, investigationExplanationFixture, replayFixture } from './fixtures'
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
    explain: vi.fn(),
    explanationRuns: vi.fn(),
  },
}))

const mockedInvestigations = vi.mocked(investigationsApi)
const replay = replayFixture()

describe('AiInvestigationPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedInvestigations.explanationRuns.mockResolvedValue([])
  })

  it('labels the assistant as read-only and shows an empty run trail', async () => {
    renderWithRouter(
      <AiInvestigationPanel
        investigationId="00000000-0000-0000-0000-000000000001"
        transactionReference={replay.transactionReference}
        evidenceNodeIds={replay.evidence.nodes.map((n) => n.id)}
      />,
    )
    expect(screen.getByText('AI investigator')).toBeInTheDocument()
    expect(screen.getByText('Read-only · never decides')).toBeInTheDocument()
    expect(await screen.findByText(/No AI investigations have run/)).toBeInTheDocument()
  })

  it('sends the preset question type and renders the grounded answer', async () => {
    const user = userEvent.setup()
    const answer = investigationExplanationFixture()
    mockedInvestigations.explain.mockResolvedValue(answer)
    mockedInvestigations.explanationRuns.mockResolvedValue([aiInvestigationRunFixture()])

    renderWithRouter(
      <AiInvestigationPanel
        investigationId="00000000-0000-0000-0000-000000000001"
        transactionReference={replay.transactionReference}
        evidenceNodeIds={replay.evidence.nodes.map((n) => n.id)}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Summarize' }))

    await waitFor(() => {
      expect(mockedInvestigations.explain).toHaveBeenCalledWith(
        '00000000-0000-0000-0000-000000000001',
        { requestType: 'SUMMARIZE' },
      )
    })

    const result = await screen.findByTestId('ai-investigator-answer')
    expect(result).toHaveTextContent(answer.summary)
    expect(result).toHaveTextContent('REVIEW')
    expect(result).toHaveTextContent('fraud-policy/v1')

    expect(await screen.findByText('SUCCEEDED')).toBeInTheDocument()
  })

  it('sends a free-form question as untrusted analyst input', async () => {
    const user = userEvent.setup()
    const answer = investigationExplanationFixture({ requestType: 'FREE_FORM' })
    mockedInvestigations.explain.mockResolvedValue(answer)

    renderWithRouter(
      <AiInvestigationPanel
        investigationId="00000000-0000-0000-0000-000000000001"
        transactionReference={replay.transactionReference}
        evidenceNodeIds={replay.evidence.nodes.map((n) => n.id)}
      />,
    )

    await user.type(
      screen.getByLabelText('Free-form question (treated as untrusted data)'),
      'Does this look like laundering?',
    )
    await user.click(screen.getByRole('button', { name: 'Ask' }))

    await waitFor(() => {
      expect(mockedInvestigations.explain).toHaveBeenCalledWith(
        '00000000-0000-0000-0000-000000000001',
        { requestType: 'FREE_FORM', freeFormQuestion: 'Does this look like laundering?' },
      )
    })
  })

  it('surfaces provider rejection without rendering an answer', async () => {
    const user = userEvent.setup()
    mockedInvestigations.explain.mockRejectedValue(
      new ApiRequestError(502, 'AI_RESPONSE_INVALID', 'AI response rejected: no evidence cited', {}),
    )

    renderWithRouter(
      <AiInvestigationPanel
        investigationId="00000000-0000-0000-0000-000000000001"
        transactionReference={replay.transactionReference}
        evidenceNodeIds={[]}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Why flagged?' }))

    expect(await screen.findByText('AI response rejected')).toBeInTheDocument()
    expect(screen.queryByTestId('ai-investigator-answer')).not.toBeInTheDocument()
  })
})