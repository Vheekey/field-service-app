import { defineStore } from 'pinia'

export const useConnectivityStore = defineStore('connectivity', {
  state: () => ({
    isOnline: navigator.onLine,
    lastSyncedAt: new Date().toISOString(),
  }),
  actions: {
    setOnline(isOnline: boolean) {
      this.isOnline = isOnline
    },
    markSynced() {
      this.lastSyncedAt = new Date().toISOString()
    },
  },
})
