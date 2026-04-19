export type RequestOptions = RequestInit & {
  accessToken?: string
  skipAuth?: boolean
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const AUTH_STORAGE_KEY = 'field-service-auth'

function storedAccessToken() {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY)

  if (!raw) {
    return null
  }

  try {
    return (JSON.parse(raw) as { accessToken?: string }).accessToken ?? null
  } catch {
    return null
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const accessToken = options.accessToken ?? (options.skipAuth ? null : storedAccessToken())

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const { accessToken: _accessToken, skipAuth: _skipAuth, ...requestOptions } = options

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.error?.message ?? `Request failed with ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
