import { describe, it, expect, beforeEach } from 'vitest'
import { MutationObserver } from '@tanstack/react-query'
import { createQueryClient } from './queryClient'
import { useToastStore } from './components/Toast'

async function runFailingMutation(meta?: Record<string, unknown>) {
  const client = createQueryClient()
  const observer = new MutationObserver(client, {
    mutationFn: () => Promise.reject(new Error('boom')),
    meta,
  })
  await observer.mutate().catch(() => {})
}

describe('createQueryClient', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] })
  })

  it('mutationErrorsAreToastedByDefault', async () => {
    await runFailingMutation()
    expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['boom'])
  })

  it('inlineErrorMutationsAreNotToasted', async () => {
    await runFailingMutation({ inlineError: true })
    expect(useToastStore.getState().toasts).toEqual([])
  })
})
