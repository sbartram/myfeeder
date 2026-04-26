import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiGet, apiPost, apiDelete } from './client'

describe('API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('should throw on non-ok response with empty body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 500 })
    )

    await expect(apiGet('/test')).rejects.toThrow('GET /test failed: 500')
  })

  it('should surface ProblemDetail.detail from error responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ title: 'Configuration error', detail: 'Raindrop.io is not configured', status: 409 }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    await expect(apiPost('/articles/1/raindrop')).rejects.toThrow('Raindrop.io is not configured')
  })

  it('should return undefined for 204 responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 204 })
    )

    const result = await apiPost('/test')
    expect(result).toBeUndefined()
  })

  it('should send JSON body for POST', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 1 }), { status: 200 })
    )

    await apiPost('/test', { name: 'foo' })

    expect(mockFetch).toHaveBeenCalledWith('/api/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"name":"foo"}',
    })
  })

  it('should send DELETE without body', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 204 })
    )

    await apiDelete('/test')

    expect(mockFetch).toHaveBeenCalledWith('/api/test', { method: 'DELETE' })
  })
})
