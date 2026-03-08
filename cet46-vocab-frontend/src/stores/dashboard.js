import { defineStore } from 'pinia'
import { getOverview, getStats } from '@/api/dashboard'

const CACHE_MS = 5 * 60 * 1000
let lastStatsDays = 30

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    overview: null,
    stats: null,
    lastFetchTime: null
  }),
  actions: {
    invalidateCache() {
      this.overview = null
      this.stats = null
      this.lastFetchTime = null
    },

    async fetchOverview(force = false) {
      const now = Date.now()
      if (!force && this.overview && this.lastFetchTime && now - this.lastFetchTime < CACHE_MS) {
        return this.overview
      }
      const res = await getOverview()
      this.overview = res?.data || null
      this.lastFetchTime = Date.now()
      return this.overview
    },

    async fetchStats(days = 30) {
      const now = Date.now()
      const sameDays = Number(days) === Number(lastStatsDays)
      if (this.stats && this.lastFetchTime && sameDays && now - this.lastFetchTime < CACHE_MS) {
        return this.stats
      }
      const res = await getStats(days)
      this.stats = res?.data || null
      this.lastFetchTime = Date.now()
      lastStatsDays = Number(days)
      return this.stats
    }
  }
})
