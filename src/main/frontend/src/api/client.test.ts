import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiGet, apiPost, apiDelete, apiDeleteJson, ApiError } from './client'

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

  it('errorsCarryStatusAndProblemTitle', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ title: 'Jev unavailable', detail: 'Jev is temporarily unavailable', status: 503 }),
        { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const error = await apiPost('/interest/preview', { articleId: 1 }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 503,
      title: 'Jev unavailable',
      message: 'Jev is temporarily unavailable',
    })
  })

  it('apiDeleteJsonParsesTheBody', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ scored: true, effects: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await apiDeleteJson<{ scored: boolean; effects: unknown[] }>('/articles/1/feedback')

    expect(result).toEqual({ scored: true, effects: [] })
    expect(mockFetch).toHaveBeenCalledWith('/api/articles/1/feedback', { method: 'DELETE' })
  })

  it('apiDeleteJsonRaisesApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ title: 'Not Found', detail: 'Article 1 not found', status: 404 }),
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const error = await apiDeleteJson('/articles/1/feedback').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, title: 'Not Found', message: 'Article 1 not found' })
  })

  it('emptyBodyErrorHasStatusAndNoTitle', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 500 }))

    const error = await apiGet('/test').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(500)
    expect(apiError.title).toBeUndefined()
    expect(apiError.message).toBe('GET /test failed: 500')
  })
})
