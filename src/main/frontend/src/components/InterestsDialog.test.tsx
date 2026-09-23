import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { InterestsDialog } from './InterestsDialog'

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

function route(method: string, url: string, handler: Handler) {
  routes[`${method} ${url}`] = handler
}

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
})
