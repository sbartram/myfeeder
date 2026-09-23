import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import type { InterestTopic } from '../api/interest'
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
}

/** Owns the row state the way InterestsDialog does, so TopicRow runs controlled. */
function Harness({
  initial,
  onSaved = () => {},
  onDeleted = () => {},
  onDiscard = () => {},
  articleId,
  previewBlock,
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
})
