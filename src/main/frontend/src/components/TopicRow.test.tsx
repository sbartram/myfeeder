import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import type { InterestTopic, TopicLearned } from '../api/interest'
import { createQueryClient } from '../queryClient'
import { TopicRow, type PreviewBlock, type TopicRowState } from './TopicRow'

type Reply = { status: number; body?: unknown }
type Handler = (init?: RequestInit) => Reply | Promise<Reply>

interface RecordedCall {
  method: string
  url: string
  body?: string
}

let routes: Record<string, Handler>
let calls: RecordedCall[]

function route(method: string, url: string, handler: Handler) {
  routes[`${method} ${url}`] = handler
}

function jsonResponse({ status, body }: Reply): Response {
  if (body === undefined) return new Response(null, { status })
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const NEGATION_WARNING =
  '⚠ This looks negated. Describe the subject positively (e.g. "about crypto") and give it a negative weight instead.'
const WEIGHT_ERROR = 'Weight must be a whole number from −50 to +50.'

/**
 * Types `text` one character at a time, 100 ms apart, with fake timers running. userEvent is
 * avoided here because Testing Library's async wrapper waits on a timer the fake clock freezes.
 */
function typeSlowly(input: HTMLElement, text: string) {
  let value = (input as HTMLInputElement).value
  for (const ch of text) {
    value += ch
    fireEvent.change(input, { target: { value } })
    act(() => {
      vi.advanceTimersByTime(100)
    })
  }
}

function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

function savedRow(overrides: Partial<TopicRowState> = {}): TopicRowState {
  return {
    key: 't-7',
    id: 7,
    name: 'Rust',
    description: 'The Rust programming language',
    weightText: '20',
    weight: 20,
    saved: { name: 'Rust', description: 'The Rust programming language', weight: 20 },
    ...overrides,
  }
}

function draftRow(overrides: Partial<TopicRowState> = {}): TopicRowState {
  return {
    key: 'd-1',
    id: null,
    name: '',
    description: '',
    weightText: '20',
    weight: 20,
    saved: null,
    ...overrides,
  }
}

interface HarnessProps {
  initial: TopicRowState
  onSaved?: (topic: InterestTopic) => void
  onDeleted?: () => void
  onDiscard?: () => void
  articleId?: number | null
  previewBlock?: PreviewBlock | null
  learned?: TopicLearned
}

/** Owns the row state the way InterestsDialog does, so TopicRow runs controlled. */
function Harness({
  initial,
  onSaved = () => {},
  onDeleted = () => {},
  onDiscard = () => {},
  articleId,
  previewBlock,
  learned,
}: HarnessProps) {
  const [row, setRow] = useState(initial)
  return (
    <TopicRow
      row={row}
      onChange={setRow}
      onSaved={onSaved}
      onDeleted={onDeleted}
      onDiscard={onDiscard}
      articleId={articleId}
      previewBlock={previewBlock}
      learned={learned}
    />
  )
}

function testQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderRow(props: HarnessProps, qc: QueryClient = testQueryClient()) {
  const result = render(
    <QueryClientProvider client={qc}>
      <Harness {...props} />
    </QueryClientProvider>,
  )
  return {
    ...result,
    /** Re-renders the same row with new props, keeping its state (e.g. a new articleId). */
    rerenderRow: (next: HarnessProps) =>
      result.rerender(
        <QueryClientProvider client={qc}>
          <Harness {...next} />
        </QueryClientProvider>,
      ),
  }
}

function previewCalls() {
  return calls.filter((c) => c.method === 'POST' && c.url === '/api/interest/preview')
}

function previewSlot(container: HTMLElement): HTMLElement {
  return container.querySelector<HTMLElement>('.interests-preview-result')!
}

/** A response the test resolves by hand, to observe the in-flight state. */
function deferred() {
  let resolve!: (reply: Reply) => void
  const promise = new Promise<Reply>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

describe('TopicRow', () => {
  beforeEach(() => {
    calls = []
    routes = {}
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
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('weightOutOfRangeShowsErrorAndDisablesSave', async () => {
    const user = userEvent.setup()
    renderRow({ initial: draftRow({ name: 'Rust', description: 'The Rust language' }) })
    const number = screen.getByRole('spinbutton', { name: 'Topic weight value' })
    const save = () => screen.getByRole('button', { name: 'Save topic: Rust' })
    expect(save()).toBeEnabled()

    for (const typed of ['51', '12.5']) {
      await user.clear(number)
      await user.type(number, typed)
      expect(number).toHaveDisplayValue(typed)
      expect(screen.getByText(WEIGHT_ERROR)).toBeInTheDocument()
      expect(save()).toBeDisabled()
    }

    await user.clear(number)
    expect(number).toHaveDisplayValue('')
    expect(screen.getByText(WEIGHT_ERROR)).toBeInTheDocument()
    expect(save()).toBeDisabled()

    await user.type(number, '-15')
    expect(screen.queryByText(WEIGHT_ERROR)).not.toBeInTheDocument()
    expect(save()).toBeEnabled()
  })

  it('negationWarningAppearsAfterDebounceAndNeverBlocks', () => {
    vi.useFakeTimers()
    renderRow({ initial: draftRow({ name: 'Crypto' }) })
    const description = screen.getByRole('textbox', { name: 'Topic description' })

    typeSlowly(description, 'not about crypto')
    // The last keystroke was 100 ms ago; the check fires 400 ms after it.
    advance(298)
    expect(screen.queryByText(NEGATION_WARNING)).not.toBeInTheDocument()

    advance(1)
    expect(screen.queryByText(NEGATION_WARNING)).not.toBeInTheDocument()
    advance(1)
    expect(screen.getByRole('status')).toHaveTextContent(NEGATION_WARNING)
    expect(screen.getByRole('button', { name: 'Save topic: Crypto' })).toBeEnabled()
    expect(description).toHaveValue('not about crypto')
  })

  it('negationWarningClears', () => {
    vi.useFakeTimers()
    renderRow({ initial: draftRow({ name: 'Crypto' }) })
    const description = screen.getByRole('textbox', { name: 'Topic description' })

    typeSlowly(description, 'not about crypto')
    advance(400)
    expect(screen.getByText(NEGATION_WARNING)).toBeInTheDocument()

    fireEvent.change(description, { target: { value: '' } })
    typeSlowly(description, 'about crypto')
    expect(screen.getByText(NEGATION_WARNING)).toBeInTheDocument()
    advance(400)
    expect(screen.queryByText(NEGATION_WARNING)).not.toBeInTheDocument()
  })

  it('blankFieldsDisableSaveWithBlurMessages', async () => {
    const user = userEvent.setup()
    renderRow({ initial: savedRow() })
    const name = screen.getByRole('textbox', { name: 'Topic name' })
    const description = screen.getByRole('textbox', { name: 'Topic description' })

    await user.clear(description)
    expect(screen.getByRole('button', { name: 'Save topic: Rust' })).toBeDisabled()
    expect(screen.queryByText('Write a description before saving.')).not.toBeInTheDocument()
    await user.tab()
    expect(screen.getByText('Write a description before saving.')).toBeInTheDocument()

    await user.type(description, 'Rust')
    expect(screen.queryByText('Write a description before saving.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save topic: Rust' })).toBeEnabled()

    await user.clear(name)
    await user.click(description)
    expect(screen.getByText('Write a short name before saving.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Save topic:/ })).toBeDisabled()
  })

  it('savedRowEditSendsPut', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    route('PUT', '/api/interest/topics/7', (init) => ({
      status: 200,
      body: {
        id: 7,
        version: 2,
        createdAt: '2026-09-23T00:00:00Z',
        updatedAt: '2026-09-23T00:00:00Z',
        ...(JSON.parse(String(init?.body)) as object),
      },
    }))
    renderRow({ initial: savedRow(), onSaved })
    expect(screen.queryByRole('button', { name: /^Save topic:/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Unsaved')).not.toBeInTheDocument()

    const number = screen.getByRole('spinbutton', { name: 'Topic weight value' })
    await user.clear(number)
    await user.type(number, '35')
    expect(screen.getByText('Unsaved')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save topic: Rust' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    const put = calls.find((c) => c.method === 'PUT')
    expect(put?.url).toBe('/api/interest/topics/7')
    expect(put?.body).toBe('{"name":"Rust","description":"The Rust programming language","weight":35}')
    expect(onSaved.mock.calls[0][0]).toMatchObject({ id: 7, weight: 35, version: 2 })
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('aDraftWithASourceSendsItOnCreate', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    route('POST', '/api/interest/topics', (init) => ({
      status: 201,
      body: {
        id: 50,
        version: 1,
        createdAt: '2026-09-23T00:00:00Z',
        updatedAt: '2026-09-23T00:00:00Z',
        ...(JSON.parse(String(init?.body)) as object),
      },
    }))
    renderRow({ initial: draftRow({ name: 'Zig', description: 'Zig comptime', sourceArticleId: 42 }), onSaved })

    await user.click(screen.getByRole('button', { name: 'Save topic: Zig' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    const post = calls.find((c) => c.method === 'POST' && c.url === '/api/interest/topics')
    expect(post?.body).toBe('{"name":"Zig","description":"Zig comptime","weight":20,"sourceArticleId":42}')
  })

  it('aSavedRowEditNeverSendsTheSource', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()
    route('PUT', '/api/interest/topics/7', (init) => ({
      status: 200,
      body: {
        id: 7,
        version: 2,
        createdAt: '2026-09-23T00:00:00Z',
        updatedAt: '2026-09-23T00:00:00Z',
        ...(JSON.parse(String(init?.body)) as object),
      },
    }))
    renderRow({ initial: savedRow({ sourceArticleId: 42 }), onSaved })

    const number = screen.getByRole('spinbutton', { name: 'Topic weight value' })
    await user.clear(number)
    await user.type(number, '35')
    await user.click(screen.getByRole('button', { name: 'Save topic: Rust' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    const put = calls.find((c) => c.method === 'PUT')
    expect(put?.body).toBe('{"name":"Rust","description":"The Rust programming language","weight":35}')
  })

  it('deleteConfirmsInlineThenDeletes', async () => {
    const user = userEvent.setup()
    const onDeleted = vi.fn()
    route('DELETE', '/api/interest/topics/7', () => ({ status: 204 }))
    renderRow({ initial: savedRow(), onDeleted })

    await user.click(screen.getByRole('button', { name: 'Delete topic: Rust' }))
    expect(
      screen.getByText("Delete this topic? Its scores and feedback are removed too. This can't be undone."),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Keep topic: Rust' }))
    expect(screen.queryByText(/Delete this topic\?/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete topic: Rust' })).toBeInTheDocument()
    expect(calls.filter((c) => c.method === 'DELETE')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Delete topic: Rust' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete topic: Rust' }))

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1))
    expect(calls.filter((c) => c.method === 'DELETE').map((c) => c.url)).toEqual([
      '/api/interest/topics/7',
    ])
  })

  it('saveAndDeleteFailuresShowInlineCopy', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/topics', () => ({
      status: 400,
      body: { title: 'Bad Request', detail: 'A maximum of 25 topics is allowed' },
    }))
    route('DELETE', '/api/interest/topics/7', () => ({ status: 500 }))

    const draft = renderRow({ initial: draftRow({ name: 'Go', description: 'The Go language' }) })
    await user.click(screen.getByRole('button', { name: 'Save topic: Go' }))
    expect(
      await screen.findByText(
        "Couldn't save this topic: A maximum of 25 topics is allowed. Your changes are still here. Try Save topic again.",
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Topic description' })).toHaveValue('The Go language')
    draft.unmount()

    renderRow({ initial: savedRow() })
    await user.click(screen.getByRole('button', { name: 'Delete topic: Rust' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete topic: Rust' }))
    expect(
      await screen.findByText("Couldn't delete this topic: DELETE /interest/topics/7 failed: 500. Try again."),
    ).toBeInTheDocument()
  })

  it('ariaLabelsNameTheTopic', async () => {
    const user = userEvent.setup()
    const saved = renderRow({ initial: savedRow() })
    expect(screen.getByRole('button', { name: 'Delete topic: Rust' })).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Topic description' }), ' 2')
    expect(screen.getByRole('button', { name: 'Save topic: Rust' })).toBeInTheDocument()
    saved.unmount()

    const long = 'a'.repeat(30) + 'b'.repeat(30)
    const nameless = renderRow({ initial: draftRow({ description: long }) })
    const label = `${'a'.repeat(30)}${'b'.repeat(10)}…`
    expect(screen.getByRole('button', { name: `Discard draft: ${label}` })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: `Save topic: ${label}` })).toBeInTheDocument()
    nameless.unmount()

    renderRow({ initial: draftRow() })
    expect(screen.getByRole('button', { name: 'Discard draft: new topic' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save topic: new topic' })).toBeDisabled()
  })
  it('previewSendsTheRowAndShowsTheMath', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/preview', () => ({ status: 200, body: { noul: 0.82, model: 'jev-1.13.0' } }))
    const { container } = renderRow({ initial: savedRow(), articleId: 1 })

    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))

    await waitFor(() =>
      expect(previewSlot(container).textContent).toBe('Match 82% → counts 64% × +20 = +12.8 pts'),
    )
    expect(previewCalls()).toHaveLength(1)
    expect(previewCalls()[0].body).toBe(
      '{"articleId":1,"description":"The Rust programming language","topicId":7}',
    )
    expect(container.querySelector('.interests-preview-points')).toHaveClass('weight-positive')
    expect(calls).toHaveLength(1)
  })

  it('draftPreviewSendsNullTopicId', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/preview', () => ({ status: 200, body: { noul: 0.9, model: 'jev-1.13.0' } }))
    renderRow({ initial: draftRow({ name: 'Go', description: '  The Go language  ' }), articleId: 3 })

    await user.click(screen.getByRole('button', { name: 'Preview topic: Go' }))

    await waitFor(() => expect(previewCalls()).toHaveLength(1))
    expect(previewCalls()[0].body).toBe('{"articleId":3,"description":"The Go language","topicId":null}')
  })

  it('inFlightShowsPreviewingAndAskingJev', async () => {
    const user = userEvent.setup()
    const reply = deferred()
    route('POST', '/api/interest/preview', () => reply.promise)
    const qc = testQueryClient()
    const { container } = render(
      <QueryClientProvider client={qc}>
        <Harness initial={savedRow()} articleId={1} />
        <Harness
          initial={savedRow({ key: 't-8', id: 8, name: 'Go', description: 'The Go language' })}
          articleId={1}
        />
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))

    const busy = screen.getByRole('button', { name: 'Preview topic: Rust' })
    expect(busy).toHaveTextContent('Previewing…')
    expect(busy).toBeDisabled()
    const slots = container.querySelectorAll('.interests-preview-result')
    expect(slots[0]).toHaveTextContent('Asking Jev…')
    expect(slots[0]).toHaveAttribute('aria-live', 'polite')
    expect(slots[1]).toBeEmptyDOMElement()
    expect(screen.getByRole('button', { name: 'Preview topic: Go' })).toBeEnabled()

    await act(async () => {
      reply.resolve({ status: 200, body: { noul: 0.4, model: 'jev-1.13.0' } })
    })
    await waitFor(() => expect(slots[0]).toHaveTextContent('Match 40% · No match (contributes 0)'))
    expect(screen.getByRole('button', { name: 'Preview topic: Rust' })).toHaveTextContent('Preview topic')
    expect(previewCalls()).toHaveLength(1)
  })
  it('previewDisabledReasonsFollowTheSpecOrder', () => {
    const cases: Array<[PreviewBlock, string]> = [
      [{ kind: 'not-configured', reason: 'no TypeSafe API key is configured.' }, 'No TypeSafe API key is configured.'],
      [
        { kind: 'breaker', reason: 'Jev is temporarily unavailable. Try again in a minute.' },
        'Jev is temporarily unavailable. Try again in a minute.',
      ],
      [
        { kind: 'no-article', reason: 'open an article in the reading pane first.' },
        'Open an article in the reading pane first.',
      ],
      [{ kind: 'status-pending', reason: '' }, 'Checking Jev status…'],
      [{ kind: 'status-unknown', reason: "couldn't check Jev status." }, "Couldn't check Jev status."],
    ]
    for (const [block, title] of cases) {
      const view = renderRow({ initial: savedRow(), articleId: 1, previewBlock: block })
      const button = screen.getByRole('button', { name: 'Preview topic: Rust' })
      expect(button).toBeDisabled()
      expect(button).toHaveAttribute('title', title)
      view.unmount()
    }

    // Blank description, no block.
    const blank = renderRow({ initial: draftRow({ name: 'Go' }), articleId: 1, previewBlock: null })
    expect(screen.getByRole('button', { name: 'Preview topic: Go' })).toHaveAttribute(
      'title',
      'Write a description first.',
    )
    blank.unmount()

    // Not configured outranks a blank description; a blank description outranks status unknown.
    const both = renderRow({
      initial: draftRow({ name: 'Go' }),
      articleId: 1,
      previewBlock: { kind: 'not-configured', reason: 'no TypeSafe API key is configured.' },
    })
    expect(screen.getByRole('button', { name: 'Preview topic: Go' })).toHaveAttribute(
      'title',
      'No TypeSafe API key is configured.',
    )
    both.unmount()
    renderRow({
      initial: draftRow({ name: 'Go' }),
      articleId: 1,
      previewBlock: { kind: 'status-unknown', reason: "couldn't check Jev status." },
    })
    expect(screen.getByRole('button', { name: 'Preview topic: Go' })).toHaveAttribute(
      'title',
      'Write a description first.',
    )
  })

  it('previewEnabledHasNoTitle', () => {
    renderRow({ initial: savedRow(), articleId: 1, previewBlock: null })
    const button = screen.getByRole('button', { name: 'Preview topic: Rust' })
    expect(button).toBeEnabled()
    expect(button).not.toHaveAttribute('title')
  })

  it('weightChangeRecomputesWithoutANewCall', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/preview', () => ({ status: 200, body: { noul: 0.82, model: 'jev-1.13.0' } }))
    const { container } = renderRow({ initial: savedRow(), articleId: 1 })
    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))
    await waitFor(() =>
      expect(previewSlot(container).textContent).toBe('Match 82% → counts 64% × +20 = +12.8 pts'),
    )

    const number = screen.getByRole('spinbutton', { name: 'Topic weight value' })
    await user.clear(number)
    await user.type(number, '-20')

    expect(previewSlot(container).textContent).toBe('Match 82% → counts 64% × −20 = −12.8 pts')
    expect(previewSlot(container)).not.toHaveClass('stale')
    expect(container.querySelector('.interests-preview-points')).toHaveClass('weight-negative')
    expect(previewCalls()).toHaveLength(1)
  })

  it('descriptionOrArticleChangeMarksTheResultStale', async () => {
    const user = userEvent.setup()
    const STALE = 'Description or article changed. Preview again to update.'
    route('POST', '/api/interest/preview', () => ({ status: 200, body: { noul: 0.82, model: 'jev-1.13.0' } }))

    const edited = renderRow({ initial: savedRow(), articleId: 1 })
    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))
    await waitFor(() => expect(previewSlot(edited.container)).toHaveTextContent('Match 82%'))
    expect(screen.queryByText(STALE)).not.toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Topic description' }), ' ecosystem')
    expect(previewSlot(edited.container)).toHaveClass('stale')
    expect(screen.getByText(STALE)).toBeInTheDocument()
    expect(previewSlot(edited.container)).toHaveTextContent('Match 82% → counts 64% × +20 = +12.8 pts')
    edited.unmount()

    const moved = renderRow({ initial: savedRow(), articleId: 1 })
    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))
    await waitFor(() => expect(previewSlot(moved.container)).toHaveTextContent('Match 82%'))
    moved.rerenderRow({ initial: savedRow(), articleId: 2 })
    expect(previewSlot(moved.container)).toHaveClass('stale')
    expect(screen.getByText(STALE)).toBeInTheDocument()
    expect(previewCalls()).toHaveLength(2)
  })

  it('noMatchShowsMutedCopy', async () => {
    const user = userEvent.setup()
    route('POST', '/api/interest/preview', () => ({ status: 200, body: { noul: 0.31, model: 'jev-1.13.0' } }))
    const { container } = renderRow({ initial: savedRow(), articleId: 1 })
    await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))

    await waitFor(() =>
      expect(previewSlot(container).textContent).toBe('Match 31% · No match (contributes 0)'),
    )
    expect(container.querySelector('.interests-preview-nomatch')).toHaveTextContent(
      'Match 31% · No match (contributes 0)',
    )
    expect(container.querySelector('.interests-preview-points')).toBeNull()
  })

  it('previewFailuresShowStatusSpecificCopy', async () => {
    const user = userEvent.setup()
    const TRANSIENT = 'Preview failed: Jev is unavailable right now. Try Preview topic again in a minute.'
    const cases: Array<[Reply, string]> = [
      [
        { status: 503, body: { title: 'Jev not configured', detail: 'No TypeSafe API key is configured' } },
        'Preview failed: no TypeSafe API key is configured.',
      ],
      // The dialog still showed CLOSED (no block), but the breaker has opened since.
      [{ status: 503, body: { title: 'Jev unavailable', detail: 'Jev is temporarily unavailable' } }, TRANSIENT],
      [{ status: 503, body: { title: 'Jev request failed', detail: 'Jev request failed' } }, TRANSIENT],
      [{ status: 429, body: { title: 'Too Many Requests' } }, TRANSIENT],
      [{ status: 500 }, TRANSIENT],
      [
        { status: 422, body: { title: 'Jev rejected the request', detail: 'Jev rejected the request (HTTP 422)' } },
        "Preview failed: Jev couldn't judge this article (Jev rejected the request (HTTP 422)). Try rewording the description.",
      ],
      [
        { status: 400, body: { title: 'Bad Request', detail: 'Article has no text to judge' } },
        "Preview failed: Jev couldn't judge this article (Article has no text to judge). Try rewording the description.",
      ],
      [
        { status: 404, body: { title: 'Not Found', detail: 'Article not found: 1' } },
        'Preview failed: Article not found: 1. Try Preview topic again.',
      ],
    ]
    for (const [reply, copy] of cases) {
      route('POST', '/api/interest/preview', () => reply)
      const view = renderRow({ initial: savedRow(), articleId: 1, previewBlock: null })
      await user.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))
      const error = await within(previewSlot(view.container)).findByText(copy)
      expect(error).toHaveClass('dialog-error')
      expect(screen.getByRole('button', { name: 'Preview topic: Rust' })).toBeEnabled()
      view.unmount()
    }
  })

  it('failedPreviewIsNeverRetried', async () => {
    route('POST', '/api/interest/preview', () => ({
      status: 503,
      body: { title: 'Jev unavailable', detail: 'Jev is temporarily unavailable' },
    }))
    // The app's own client, so a default mutation retry would show up here.
    const { container } = renderRow({ initial: savedRow(), articleId: 1 }, createQueryClient())
    fireEvent.click(screen.getByRole('button', { name: 'Preview topic: Rust' }))
    await within(previewSlot(container)).findByText(/^Preview failed: Jev is unavailable right now/)

    vi.useFakeTimers()
    advance(5000)
    vi.useRealTimers()

    expect(previewCalls()).toHaveLength(1)
    expect(previewSlot(container)).toHaveTextContent('Preview failed: Jev is unavailable right now.')
  })

  describe('learned line', () => {
    function learned(overrides: Partial<TopicLearned> = {}): TopicLearned {
      return {
        topicId: 7,
        baseWeight: 20,
        learned: 4,
        effectiveWeight: 24,
        limit: 'NONE',
        thumbsLearned: 4,
        engagementLearned: 0,
        ...overrides,
      }
    }

    function learnedLine(container: HTMLElement) {
      return container.querySelector('.interests-learned')
    }

    it('learnedLineShowsLearnedAndEffective', () => {
      const { container } = renderRow({ initial: savedRow(), learned: learned() })

      expect(learnedLine(container)?.textContent).toBe('Learned +4.0 (votes +4.0) · Effective weight +24.0')
      expect(screen.getByRole('spinbutton', { name: 'Topic weight value' })).toHaveValue(20)
      expect(container.querySelector('.interests-weight-sign')).toHaveTextContent('+20')
    })

    it('learnedLineSplitsVotesAndEngagement', () => {
      const { container } = renderRow({
        initial: savedRow(),
        learned: learned({
          baseWeight: 10,
          learned: 3.5,
          thumbsLearned: 2.0,
          engagementLearned: 1.5,
          effectiveWeight: 13.5,
        }),
      })

      expect(learnedLine(container)?.textContent).toBe(
        'Learned +3.5 (votes +2.0, engaged +1.5) · Effective weight +13.5',
      )
    })

    it('engagedOnlyLine', () => {
      const { container } = renderRow({
        initial: savedRow(),
        learned: learned({ learned: 1.5, thumbsLearned: 0, engagementLearned: 1.5, effectiveWeight: 21.5 }),
      })

      expect(learnedLine(container)?.textContent).toBe('Learned +1.5 (engaged +1.5) · Effective weight +21.5')
    })

    it('zeroPartsAreOmitted', () => {
      const { container } = renderRow({
        initial: savedRow(),
        learned: learned({ learned: 0.08, thumbsLearned: 0.04, engagementLearned: 0.04, effectiveWeight: 20.1 }),
      })

      expect(learnedLine(container)?.textContent).toBe('Learned +0.1 · Effective weight +20.1')
    })

    it('noLearnedAdjustmentYet', () => {
      const { container } = renderRow({
        initial: savedRow(),
        learned: learned({ learned: 0.04, effectiveWeight: 20 }),
      })

      expect(learnedLine(container)?.textContent).toBe('No learned adjustment yet · Effective weight +20')
    })

    it('limitSuffixes', () => {
      const cases: [Partial<TopicLearned>, string][] = [
        [
          { learned: 20, thumbsLearned: 20, effectiveWeight: 40, limit: 'LEARNED_CAP' },
          'Learned +20.0 (votes +20.0 at max) · Effective weight +40.0',
        ],
        [
          { learned: -20, thumbsLearned: -20, effectiveWeight: 0, limit: 'LEARNED_CAP' },
          'Learned −20.0 (votes −20.0 at min) · Effective weight 0.0',
        ],
        [
          { learned: -20, thumbsLearned: -20, effectiveWeight: 0, limit: 'SIGN_CLAMP' },
          "Learned −20.0 (votes −20.0) · Effective weight 0.0 (can't cross 0)",
        ],
        [
          { baseWeight: 45, learned: 5, thumbsLearned: 5, effectiveWeight: 50, limit: 'WEIGHT_RANGE' },
          'Learned +5.0 (votes +5.0) · Effective weight +50.0 (at the +50 limit)',
        ],
        [
          { baseWeight: -45, learned: -5, thumbsLearned: -5, effectiveWeight: -50, limit: 'WEIGHT_RANGE' },
          'Learned −5.0 (votes −5.0) · Effective weight −50.0 (at the −50 limit)',
        ],
        [
          { baseWeight: 45, learned: 5, thumbsLearned: 5, effectiveWeight: 50, limit: 'NONE' },
          'Learned +5.0 (votes +5.0) · Effective weight +50.0',
        ],
        // D-15: engagement at its cap reads "at max" on the engaged part, never as votes.
        [
          {
            baseWeight: 20,
            learned: 8,
            effectiveWeight: 28,
            limit: 'ENGAGEMENT_CAP',
            thumbsLearned: 0,
            engagementLearned: 8,
          },
          'Learned +8.0 (engaged +8.0 at max) · Effective weight +28.0',
        ],
        [
          {
            baseWeight: 45,
            learned: 8,
            effectiveWeight: 50,
            limit: 'WEIGHT_RANGE',
            thumbsLearned: 0,
            engagementLearned: 8,
            engagementAtCap: true,
          },
          'Learned +8.0 (engaged +8.0 at max) · Effective weight +50.0 (at the +50 limit)',
        ],
        [
          {
            baseWeight: 45,
            learned: 5,
            effectiveWeight: 50,
            limit: 'WEIGHT_RANGE',
            thumbsLearned: 0,
            engagementLearned: 5,
            engagementAtCap: false,
          },
          'Learned +5.0 (engaged +5.0) · Effective weight +50.0 (at the +50 limit)',
        ],
      ]
      for (const [overrides, expected] of cases) {
        const view = renderRow({ initial: savedRow(), learned: learned(overrides) })
        expect(learnedLine(view.container)?.textContent).toBe(expected)
        view.unmount()
      }
    })

    it('unsavedBaseEditSaysItUpdatesOnSave', () => {
      const { container } = renderRow({ initial: savedRow(), learned: learned() })

      fireEvent.change(screen.getByRole('spinbutton', { name: 'Topic weight value' }), {
        target: { value: '30' },
      })

      expect(learnedLine(container)?.textContent).toBe(
        'Learned +4.0 (votes +4.0) · Effective weight updates when you save',
      )
    })

    it('draftsAndMissingEntriesShowNoLine', () => {
      const draft = renderRow({ initial: draftRow(), learned: learned() })
      expect(learnedLine(draft.container)).toBeNull()
      draft.unmount()

      const missing = renderRow({ initial: savedRow() })
      expect(learnedLine(missing.container)).toBeNull()
    })

    it('draftScrollsIntoView', () => {
      const scrollIntoView = vi.fn()
      Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
        configurable: true,
        writable: true,
        value: scrollIntoView,
      })
      try {
        const saved = renderRow({ initial: savedRow() })
        expect(scrollIntoView).not.toHaveBeenCalled()
        saved.unmount()

        renderRow({ initial: draftRow() })
        expect(scrollIntoView).toHaveBeenCalledTimes(1)
        expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' })
      } finally {
        delete (HTMLElement.prototype as { scrollIntoView?: unknown }).scrollIntoView
      }
    })
  })
})
