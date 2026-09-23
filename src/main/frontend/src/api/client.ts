const BASE_URL = '/api'

async function parseBody<T>(res: Response): Promise<T> {
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** An HTTP error response. `title` is the ProblemDetail title, when the server sent one. */
export class ApiError extends Error {
  readonly status: number
  readonly title?: string

  constructor(message: string, status: number, title?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = title
  }
}

async function raiseIfBad(res: Response, method: string, path: string): Promise<void> {
  if (res.ok) return
  const text = await res.text()
  let detail: string | undefined
  let title: string | undefined
  if (text) {
    try {
      const body = JSON.parse(text) as { detail?: string; title?: string; message?: string }
      detail = body.detail || body.title || body.message
      title = body.title
    } catch {
      detail = text
    }
  }
  throw new ApiError(detail || `${method} ${path} failed: ${res.status}`, res.status, title)
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`)
  await raiseIfBad(res, 'GET', path)
  return parseBody<T>(res)
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  await raiseIfBad(res, 'POST', path)
  return parseBody<T>(res)
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  await raiseIfBad(res, 'PUT', path)
  return parseBody<T>(res)
}

export async function apiPatch<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  await raiseIfBad(res, 'PATCH', path)
  return parseBody<T>(res)
}

export async function apiDelete(path: string): Promise<void> {
  const res = await fetch(`${BASE_URL}${path}`, { method: 'DELETE' })
  await raiseIfBad(res, 'DELETE', path)
}
