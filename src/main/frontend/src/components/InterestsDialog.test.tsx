import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { InterestsDialog } from './InterestsDialog'
import { createQueryClient } from '../queryClient'
import { useToastStore } from './Toast'

type Reply = { status: number; body?: unknown }
type Handler = (init?: RequestInit) => Reply | Promise<Reply>

interface RecordedCall {
  method: string
  url: string
  body?: string
}

let routes: Record<string, Handler>
let calls: RecordedCall[]

function jsonResponse({ status, body }: Reply): Response {
  if (body === undefined) return new Response(null, { status })
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function profile(profileText: string) {
  return { id: 1, profileText, version: 1, updatedAt: '2026-09-23T00:00:00Z' }
}

function topic(id: number, name: string, description: string, weight = 20) {
  return {
    id,
    name,
    description,
    weight,
    version: 1,
    createdAt: '2026-09-23T00:00:00Z',
    updatedAt: '2026-09-23T00:00:00Z',
  }
}

function topicRows(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>('.interests-topic-row'))
}

function statusFetches() {
  return calls.filter((c) => c.method === 'GET' && c.url === '/api/interest/status')
}

function route(method: string, url: string, handler: Handler) {
  routes[`${method} ${url}`] = handler
}

function status(overrides: Partial<{ configured: boolean; breakerState: string; coldStart: boolean }>) {
  route('GET', '/api/interest/status', () => ({
    status: 200,
    body: { configured: true, breakerState: 'CLOSED', coldStart: false, ...overrides },
  }))
}

function profilePuts() {
  return calls.filter((c) => c.method === 'PUT' && c.url === '/api/interest/profile')
}

const CONFIRM_COPY = 'Discard unsaved changes? You have unsaved edits to the profile.'

function renderDialog(ui: ReactElement, client?: QueryClient) {
  const qc =
    client ??
    new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('InterestsDialog', () => {
  beforeEach(() => {
    calls = []
    routes = {}
    route('GET', '/api/interest/status', () => ({
      status: 200,
      body: { configured: true, breakerState: 'CLOSED', coldStart: false },
    }))
    route('GET', '/api/interest/profile', () => ({ status: 200, body: profile('') }))
    route('GET', '/api/interest/topics', () => ({ status: 200, body: [] }))
    route('PUT', '/api/interest/profile', (init) => {
      const { profileText } = JSON.parse(String(init?.body)) as { profileText: string }
      return { status: 200, body: { ...profile(profileText), version: 2 } }
    })
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      calls.push({ method, url, body: init?.body as string | undefined })
      const handler = routes[`${method} ${url}`]
      if (!handler) return new Response(null, { status: 404 })
      return jsonResponse(await handler(init))
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('rendersNothingWhenClosed', () => {
    const { container } = renderDialog(<InterestsDialog open={false} onClose={() => {}} />)
    expect(container).toBeEmptyDOMElement()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('showsLoadingThenTheProfileEditor', async () => {
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    expect(screen.getByText('Loading interests…')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Interest profile' })).not.toBeInTheDocument()
    expect(await screen.findByRole('textbox', { name: 'Interest profile' })).toBeInTheDocument()
    expect(screen.queryByText('Loading interests…')).not.toBeInTheDocument()
  })

  it('loadsProfileAndSavesItThroughTheApi', async () => {
    const user = userEvent.setup()
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    const textarea = await screen.findByRole('textbox', { name: 'Interest profile' })
    const save = screen.getByRole('button', { name: 'Save profile' })
    expect(save).toBeDisabled()

    await user.type(textarea, 'Rust')
    expect(screen.getByText('4 / 2,000')).toBeInTheDocument()
    expect(screen.getByText('Unsaved changes')).toBeInTheDocument()
    await user.click(save)

    await waitFor(() => expect(screen.getByText('Saved')).toBeInTheDocument())
    const put = calls.find((c) => c.method === 'PUT' && c.url === '/api/interest/profile')
    expect(put?.body).toBe('{"profileText":"Rust"}')
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled()
  })

  it('showsTipsPlaceholderAndCounter', async () => {
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    const textarea = await screen.findByRole('textbox', { name: 'Interest profile' })
    expect(container.querySelectorAll('.interests-tips li')).toHaveLength(4)
    expect(textarea.getAttribute('placeholder')).toMatch(/^I'm a backend engineer\./)
    expect(textarea).toHaveAttribute('maxLength', '2000')
    expect(screen.getByText('0 / 2,000')).toBeInTheDocument()
  })

  it('counterAtLimitShowsLimitReached', async () => {
    route('GET', '/api/interest/profile', () => ({ status: 200, body: profile('x'.repeat(2000)) }))
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    expect(await screen.findByText('2,000 / 2,000 · limit reached')).toBeInTheDocument()
  })

  it('saveFailureKeepsTextAndShowsInlineCopy', async () => {
    const user = userEvent.setup()
    route('PUT', '/api/interest/profile', () => ({
      status: 400,
      body: { title: 'Bad Request', detail: 'The profile can be at most 2,000 characters' },
    }))
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    const textarea = await screen.findByRole('textbox', { name: 'Interest profile' })
    await user.type(textarea, 'Kubernetes')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(
      await screen.findByText(
        "Couldn't save the profile: The profile can be at most 2,000 characters. Your text is still here. Try Save profile again.",
      ),
    ).toBeInTheDocument()
    expect(textarea).toHaveValue('Kubernetes')
  })

  it('loadFailureShowsRetryCopy', async () => {
    route('GET', '/api/interest/profile', () => ({ status: 500 }))
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    expect(
      await screen.findByText(
        "Couldn't load your interests: GET /interest/profile failed: 500. Close this dialog and open it again to retry.",
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
  })

  it('notConfiguredNoticeShownAndProfileStillSaves', async () => {
    const user = userEvent.setup()
    status({ configured: false })
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    expect(await screen.findByText("Scoring isn't set up yet.")).toBeInTheDocument()
    expect(screen.getByText('MYFEEDER_TYPESAFE_API_KEY')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Interest profile' }), 'Java')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    await waitFor(() => expect(screen.getByText('Saved')).toBeInTheDocument())
    expect(profilePuts().map((c) => c.body)).toEqual(['{"profileText":"Java"}'])
  })

  it('noticesStackInFixedOrder', async () => {
    status({ configured: false, breakerState: 'OPEN', coldStart: true })
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    await screen.findByText('Start here.')
    const leads = Array.from(container.querySelectorAll('.interests-notice strong')).map(
      (el) => el.textContent,
    )
    expect(leads).toEqual([
      "Scoring isn't set up yet.",
      'Jev is temporarily unavailable.',
      'Start here.',
    ])
    expect(container.querySelector('.interests-notice:last-child')).toHaveClass('cold-start')
  })

  it('showsPausedNoticeForForcedOpenBreaker', async () => {
    status({ breakerState: 'FORCED_OPEN' })
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    expect(await screen.findByText('Jev is temporarily unavailable.')).toBeInTheDocument()
    expect(container.querySelectorAll('.interests-notice')).toHaveLength(1)
  })

  it('coldStartFocusesTheTextareaAndClearsAfterSave', async () => {
    const user = userEvent.setup()
    let coldStart = true
    route('GET', '/api/interest/status', () => ({
      status: 200,
      body: { configured: true, breakerState: 'CLOSED', coldStart },
    }))
    route('PUT', '/api/interest/profile', (init) => {
      coldStart = false
      const { profileText } = JSON.parse(String(init?.body)) as { profileText: string }
      return { status: 200, body: profile(profileText) }
    })
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    expect(await screen.findByText('Start here.')).toBeInTheDocument()
    const textarea = screen.getByRole('textbox', { name: 'Interest profile' })
    await waitFor(() => expect(textarea).toHaveFocus())

    await user.type(textarea, 'Postgres')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    await waitFor(() => expect(screen.queryByText('Start here.')).not.toBeInTheDocument())
    expect(calls.filter((c) => c.url === '/api/interest/status')).toHaveLength(2)
  })

  it('doesNotAutofocusWithoutColdStart', async () => {
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    const textarea = await screen.findByRole('textbox', { name: 'Interest profile' })
    await waitFor(() => expect(calls.some((c) => c.url === '/api/interest/status')).toBe(true))
    expect(textarea).not.toHaveFocus()
  })

  it('statusFailureShowsNoNoticeAndEditingWorks', async () => {
    const user = userEvent.setup()
    route('GET', '/api/interest/status', () => ({ status: 500 }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    const textarea = await screen.findByRole('textbox', { name: 'Interest profile' })
    await user.type(textarea, 'Go')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    await waitFor(() => expect(screen.getByText('Saved')).toBeInTheDocument())
    expect(container.querySelector('.interests-notice')).toBeNull()
    expect(profilePuts()).toHaveLength(1)
  })

  it('closeWhileDirtyAsksFirst', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await user.type(await screen.findByRole('textbox', { name: 'Interest profile' }), 'Rust')

    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.getByText(CONFIRM_COPY)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Keep editing' }))
    expect(screen.queryByText(CONFIRM_COPY)).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Interest profile' })).toHaveValue('Rust')

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(screen.getByRole('button', { name: 'Discard changes' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closeWhenCleanClosesImmediately', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await screen.findByRole('textbox', { name: 'Interest profile' })

    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.queryByText(CONFIRM_COPY)).not.toBeInTheDocument()
  })

  it('overlayClickUsesTheSameGuard', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const { container } = renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await user.type(await screen.findByRole('textbox', { name: 'Interest profile' }), 'Rust')

    const overlay = container.querySelector('.dialog-overlay')!
    fireEvent.click(overlay)
    expect(screen.getByText(CONFIRM_COPY)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('dialog'))
    expect(onClose).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Keep editing' }))
    await user.clear(screen.getByRole('textbox', { name: 'Interest profile' }))
    fireEvent.click(overlay)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('saveErrorIsNotToasted', async () => {
    const user = userEvent.setup()
    useToastStore.setState({ toasts: [] })
    route('PUT', '/api/interest/profile', () => ({
      status: 400,
      body: { title: 'Bad Request', detail: 'The profile can be at most 2,000 characters' },
    }))
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />, createQueryClient())

    await user.type(await screen.findByRole('textbox', { name: 'Interest profile' }), 'Rust')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText(/Couldn't save the profile/)).toBeInTheDocument()
    expect(useToastStore.getState().toasts).toEqual([])
  })
  it('addsADraftTopicAndSavesItInPlace', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/topics', (init) => ({
      status: 201,
      body: { ...topic(7, '', ''), ...(JSON.parse(String(init?.body)) as object) },
    }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    expect(await screen.findByText('No topics yet.')).toBeInTheDocument()
    expect(screen.getByText('0 / 25')).toBeInTheDocument()
    await waitFor(() => expect(statusFetches()).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: '+ Add topic' }))
    const row = topicRows(container)[0]
    expect(within(row).getByRole('textbox', { name: 'Topic name' })).toHaveFocus()
    expect(within(row).getByRole('spinbutton', { name: 'Topic weight value' })).toHaveValue(20)
    expect(row.querySelector('.interests-weight-sign')).toHaveTextContent('+20')
    expect(within(row).getByText('Unsaved')).toBeInTheDocument()

    await user.type(within(row).getByRole('textbox', { name: 'Topic name' }), 'Rust')
    await user.type(
      within(row).getByRole('textbox', { name: 'Topic description' }),
      'The Rust programming language',
    )
    await user.click(screen.getByRole('button', { name: 'Save topic: Rust' }))

    await waitFor(() => expect(within(row).queryByText('Unsaved')).not.toBeInTheDocument())
    const post = calls.find((c) => c.method === 'POST' && c.url === '/api/interest/topics')
    expect(post?.body).toBe(
      '{"name":"Rust","description":"The Rust programming language","weight":20}',
    )
    expect(within(row).queryByRole('button', { name: /Save topic/ })).not.toBeInTheDocument()
    expect(topicRows(container)[0]).toBe(row)
    expect(screen.getByText('1 / 25')).toBeInTheDocument()
    await waitFor(() => expect(statusFetches()).toHaveLength(2))
  })

  it('listsSavedTopicsInIdOrder', async () => {
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: [topic(9, 'Kubernetes', 'Running Kubernetes at home'), topic(3, 'Rust', 'The Rust language')],
    }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    await screen.findByText('2 / 25')
    const names = topicRows(container).map(
      (row) => (within(row).getByRole('textbox', { name: 'Topic name' }) as HTMLInputElement).value,
    )
    expect(names).toEqual(['Rust', 'Kubernetes'])
    expect(screen.queryByText('No topics yet.')).not.toBeInTheDocument()
  })

  it('sliderAndNumberStaySynced', async () => {
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: [topic(3, 'Crypto', 'Cryptocurrency and blockchains')],
    }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    await screen.findByText('1 / 25')
    const row = topicRows(container)[0]

    fireEvent.change(within(row).getByRole('slider', { name: 'Topic weight' }), {
      target: { value: '-15' },
    })

    expect(within(row).getByRole('spinbutton', { name: 'Topic weight value' })).toHaveValue(-15)
    const sign = row.querySelector('.interests-weight-sign')
    expect(sign).toHaveTextContent('−15')
    expect(sign).toHaveClass('weight-negative')
  })

  it('discardDraftSendsNoRequest', async () => {
    const user = userEvent.setup()
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    await screen.findByText('No topics yet.')

    await user.click(screen.getByRole('button', { name: '+ Add topic' }))
    expect(topicRows(container)).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Discard draft: new topic' }))

    expect(topicRows(container)).toHaveLength(0)
    expect(screen.getByText('No topics yet.')).toBeInTheDocument()
    expect(calls.filter((c) => c.url.startsWith('/api/interest/topics') && c.method !== 'GET')).toEqual([])
  })
  it('savingOneRowKeepsAnotherRowsUnsavedEdits', async () => {
    const user = userEvent.setup()
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: [topic(3, 'Rust', 'The Rust language'), topic(9, 'Go', 'The Go language')],
    }))
    route('PUT', '/api/interest/topics/3', (init) => ({
      status: 200,
      body: { ...topic(3, '', ''), ...(JSON.parse(String(init?.body)) as object), version: 2 },
    }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    await screen.findByText('2 / 25')
    const [first, second] = topicRows(container)

    await user.type(within(first).getByRole('textbox', { name: 'Topic description' }), ' and Cargo')
    await user.type(within(second).getByRole('textbox', { name: 'Topic description' }), ' and its tooling')
    await user.click(screen.getByRole('button', { name: 'Save topic: Rust' }))

    await waitFor(() => expect(within(first).queryByText('Unsaved')).not.toBeInTheDocument())
    expect(within(first).getByRole('textbox', { name: 'Topic description' })).toHaveValue(
      'The Rust language and Cargo',
    )
    expect(within(second).getByRole('textbox', { name: 'Topic description' })).toHaveValue(
      'The Go language and its tooling',
    )
    expect(within(second).getByText('Unsaved')).toBeInTheDocument()
    expect(second).toHaveClass('dirty')
    expect(calls.filter((c) => c.method === 'PUT').map((c) => c.url)).toEqual([
      '/api/interest/topics/3',
    ])
  })

  it('addTopicDisabledAt25WithTitle', async () => {
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: Array.from({ length: 25 }, (_, i) => topic(i + 1, `Topic ${i + 1}`, `Subject ${i + 1}`)),
    }))
    renderDialog(<InterestsDialog open={true} onClose={() => {}} />)

    expect(await screen.findByText('25 / 25')).toBeInTheDocument()
    const add = screen.getByRole('button', { name: '+ Add topic' })
    expect(add).toBeDisabled()
    expect(add).toHaveAttribute('title', 'You have 25 topics, the maximum. Delete one to add another.')
  })

  it('closeGuardCountsDirtyTopics', async () => {
    const user = userEvent.setup()
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: [topic(3, 'Rust', 'The Rust language'), topic(9, 'Go', 'The Go language')],
    }))
    const onClose = vi.fn()
    const first = renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await user.type(await screen.findByRole('textbox', { name: 'Interest profile' }), 'Java')
    for (const row of topicRows(first.container)) {
      await user.type(within(row).getByRole('textbox', { name: 'Topic name' }), '!')
    }

    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(
      screen.getByText('Discard unsaved changes? You have unsaved edits to the profile and 2 topics.'),
    ).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    first.unmount()

    const second = renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await screen.findByText('2 / 25')
    await user.type(
      within(topicRows(second.container)[1]).getByRole('spinbutton', { name: 'Topic weight value' }),
      '5',
    )
    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(
      screen.getByText('Discard unsaved changes? You have unsaved edits to 1 topic.'),
    ).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('blankDraftDoesNotBlockClose', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const { container } = renderDialog(<InterestsDialog open={true} onClose={onClose} />)
    await screen.findByText('No topics yet.')

    await user.click(screen.getByRole('button', { name: '+ Add topic' }))
    expect(topicRows(container)).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.queryByText(/Discard unsaved changes\?/)).not.toBeInTheDocument()
  })

  it('deleteRemovesTheRowAndRefreshesStatus', async () => {
    const user = userEvent.setup()
    route('GET', '/api/interest/topics', () => ({
      status: 200,
      body: [topic(3, 'Rust', 'The Rust language')],
    }))
    route('DELETE', '/api/interest/topics/3', () => ({ status: 204 }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    await screen.findByText('1 / 25')
    await waitFor(() => expect(statusFetches()).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: 'Delete topic: Rust' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete topic: Rust' }))

    await waitFor(() => expect(topicRows(container)).toHaveLength(0))
    expect(screen.getByText('0 / 25')).toBeInTheDocument()
    expect(screen.getByText('No topics yet.')).toBeInTheDocument()
    expect(calls.filter((c) => c.method === 'DELETE').map((c) => c.url)).toEqual([
      '/api/interest/topics/3',
    ])
    await waitFor(() => expect(statusFetches()).toHaveLength(2))
  })

  it('topicsStayEditableWithoutAKey', async () => {
    const user = userEvent.setup()
    status({ configured: false })
    route('POST', '/api/interest/topics', (init) => ({
      status: 201,
      body: { ...topic(4, '', ''), ...(JSON.parse(String(init?.body)) as object) },
    }))
    const { container } = renderDialog(<InterestsDialog open={true} onClose={() => {}} />)
    expect(await screen.findByText("Scoring isn't set up yet.")).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '+ Add topic' }))
    const row = topicRows(container)[0]
    await user.type(within(row).getByRole('textbox', { name: 'Topic name' }), 'Crypto')
    await user.type(within(row).getByRole('textbox', { name: 'Topic description' }), 'Cryptocurrency')
    await user.click(screen.getByRole('button', { name: 'Save topic: Crypto' }))

    await waitFor(() => expect(within(row).queryByText('Unsaved')).not.toBeInTheDocument())
    expect(
      calls.filter((c) => c.method === 'POST' && c.url === '/api/interest/topics').map((c) => c.body),
    ).toEqual(['{"name":"Crypto","description":"Cryptocurrency","weight":20}'])
  })
})
