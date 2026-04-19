import { apiRequest } from './client'
import type { AuthTokenResponse, CurrentUser, LoginRequest } from '@/types/auth'

export function login(request: LoginRequest) {
  return apiRequest<AuthTokenResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function refreshToken() {
  return apiRequest<AuthTokenResponse>('/auth/refresh', {
    method: 'POST',
    skipAuth: true,
  })
}

export function logout() {
  return apiRequest<void>('/auth/logout', {
    method: 'POST',
    skipAuth: true,
  })
}

export function getMe() {
  return apiRequest<CurrentUser>('/me')
}
