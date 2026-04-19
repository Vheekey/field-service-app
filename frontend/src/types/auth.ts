import type { GeoPoint } from './task'

export type UserRole = 'ADMIN' | 'DISPATCHER' | 'FIELD_WORKER'

export type AuthTokenResponse = {
  accessToken: string
  expiresAt: string
}

export type LoginRequest = {
  email: string
  password: string
}

export type WorkerProfile = {
  id: string
  userId: string
  active: boolean
  homeBaseName?: string
  lastKnownLocation?: GeoPoint
  lastSeenAt?: string
}

export type CurrentUser = {
  id: string
  email: string
  name: string
  roles: UserRole[]
  workerProfile?: WorkerProfile | null
}
