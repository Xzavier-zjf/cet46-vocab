import { defineStore } from 'pinia'
import request from '@/api/request'
import { getToken, setToken, removeToken } from '@/utils/token'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userId: null,
    nickname: '',
    role: '',
    llmStyle: null,
    dailyTarget: 20
  }),
  actions: {
    setUserInfo(loginResponse) {
      const data = loginResponse || {}
      this.token = data.token || ''
      this.userId = data.userId ?? null
      this.nickname = data.nickname || ''
      this.role = data.role || ''
      this.llmStyle = data.llmStyle ?? null
      this.dailyTarget = data.dailyTarget ?? 20
      if (this.token) {
        setToken(this.token)
      }
    },
    clearUserInfo() {
      this.token = ''
      this.userId = null
      this.nickname = ''
      this.role = ''
      this.llmStyle = null
      this.dailyTarget = 20
      removeToken()
    },
    async fetchUserInfo() {
      const res = await request.get('/user/info')
      if (res?.code === 200 && res.data) {
        this.userId = res.data.userId ?? this.userId
        this.nickname = res.data.nickname || ''
        this.role = res.data.role || ''
        this.llmStyle = res.data.llmStyle ?? null
        this.dailyTarget = res.data.dailyTarget ?? 20
      }
      return res
    }
  }
})
