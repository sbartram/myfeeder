const BASE_URL = '/api'

async function parseBody<T>(res: Response): Promise<T> {
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function raiseIfBad(res: Response, method: string, path: string): Promise<void> {
  if (res.ok) return
  const text = await res.text()
  let detail: string | undefined
  if (text) {
    try {
      const body = JSON.parse(text) as { detail?: string; title?: string; message?: string }
      detail = body.detail || body.title || body.message
    } catch {
      detail = text
    }
  }
  throw new Error(detail || `${method} ${path} failed: ${res.status}`)
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
