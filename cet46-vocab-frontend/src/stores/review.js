import { defineStore } from 'pinia'
import { getTodayList, submitReview } from '@/api/review'
import { useDashboardStore } from '@/stores/dashboard'

const initialState = () => ({
  sessionState: 'idle',
  wordList: [],
  currentIndex: 0,
  isFlipped: false,
  submitError: null,
  sessionStats: { total: 0, reviewed: 0, scores: [] }
})

export const useReviewStore = defineStore('review', {
  state: () => initialState(),
  getters: {
    currentWord(state) {
      return state.wordList[state.currentIndex] || null
    }
  },
  actions: {
    async startSession() {
      this.sessionState = 'loading'
      this.wordList = []
      this.currentIndex = 0
      this.isFlipped = false
      this.submitError = null
      this.sessionStats = { total: 0, reviewed: 0, scores: [] }

      const res = await getTodayList()
      const list = Array.isArray(res?.data?.list) ? res.data.list : []
      this.wordList = list
      this.sessionStats.total = Number(res?.data?.total || list.length || 0)
      this.sessionState = 'ready'

      if (list.length === 0) {
        this.sessionState = 'empty'
        return
      }
      this.sessionState = 'card_front'
      this.isFlipped = false
    },

    flipCard() {
      if (this.sessionState !== 'card_front') return
      this.isFlipped = true
      this.sessionState = 'card_back'
    },

    async submitScore(score, timeSpentMs) {
      const word = this.currentWord
      if (!word) return false
      this.sessionState = 'submitting'
      this.submitError = null

      try {
        await submitReview({
          wordId: word.wordId,
          wordType: word.wordType,
          score,
          timeSpentMs
        })
        useDashboardStore().invalidateCache()
        this.sessionStats.reviewed += 1
        this.sessionStats.scores.push(score)
        this.nextCard()
        return true
      } catch (error) {
        this.submitError = error?.response?.data?.message || error?.message || '提交失败，请重试'
        this.sessionState = 'error'
        return false
      }
    },

    nextCard() {
      this.currentIndex += 1
      this.isFlipped = false
      if (this.currentIndex >= this.wordList.length) {
        this.sessionState = 'complete'
        return
      }
      this.sessionState = 'card_front'
    },

    async retrySubmit(score, timeSpentMs) {
      if (this.sessionState !== 'error') return false
      this.submitError = null
      return this.submitScore(score, timeSpentMs)
    },

    resetSession() {
      Object.assign(this, initialState())
    }
  }
})
