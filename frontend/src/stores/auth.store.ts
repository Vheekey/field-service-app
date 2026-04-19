import { defineStore } from 'pinia'

import { getMe, login, logout, refreshToken } from '@/api/auth.api'
import type { AuthTokenResponse, CurrentUser } from '@/types/auth'

const STORAGE_KEY = 'field-service-auth'

type PersistedAuth = {
  accessToken: string
  expiresAt: string
  user: CurrentUser | null
}

function readPersistedAuth(): PersistedAuth | null {
  const raw = localStorage.getItem(STORAGE_KEY)

  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as PersistedAuth
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function isExpired(expiresAt: string) {
  return new Date(expiresAt).getTime() <= Date.now() + 30_000
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    expiresAt: null as string | null,
    hasHydrated: false,
    isLoading: false,
    user: null as CurrentUser | null,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken && state.expiresAt && !isExpired(state.expiresAt)),
    isFieldWorker: (state) => Boolean(state.user?.roles.includes('FIELD_WORKER')),
    canViewAllTasks: (state) => Boolean(state.user?.roles.some((role) => role === 'ADMIN' || role === 'DISPATCHER')),
    workerId: (state) => state.user?.id ?? null,
  },
  actions: {
    persist() {
      if (!this.accessToken || !this.expiresAt) {
        localStorage.removeItem(STORAGE_KEY)
        return
      }

      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          accessToken: this.accessToken,
          expiresAt: this.expiresAt,
          user: this.user,
        }),
      )
    },
    setSession(token: AuthTokenResponse, user?: CurrentUser | null) {
      this.accessToken = token.accessToken
      this.expiresAt = token.expiresAt
      this.user = user === undefined ? this.user : user
      this.persist()
    },
    clearSession() {
      this.accessToken = null
      this.expiresAt = null
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    },
    hydrateFromStorage() {
      if (this.hasHydrated) {
        return
      }

      const persisted = readPersistedAuth()

      if (persisted) {
        this.accessToken = persisted.accessToken
        this.expiresAt = persisted.expiresAt
        this.user = persisted.user
      }

      this.hasHydrated = true
    },
    async initialize() {
      this.hydrateFromStorage()

      if (!this.accessToken || !this.expiresAt || isExpired(this.expiresAt)) {
        await this.refreshSession()
        return
      }

      if (!this.user) {
        await this.loadCurrentUser()
      }
    },
    async signIn(email: string, password: string) {
      this.isLoading = true

      try {
        const token = await login({ email, password })
        this.setSession(token, null)
        await this.loadCurrentUser()
      } finally {
        this.isLoading = false
      }
    },
    async refreshSession() {
      try {
        const token = await refreshToken()
        this.setSession(token)
        await this.loadCurrentUser()
      } catch {
        this.clearSession()
      }
    },
    async loadCurrentUser() {
      this.user = await getMe()
      this.persist()
    },
    async signOut() {
      try {
        await logout()
      } finally {
        this.clearSession()
      }
    },
  },
})
