import { defineStore } from 'pinia'
import request from '@/api/request'
import { getToken, setToken, removeToken } from '@/utils/token'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userId: null,
    username: '',
    nickname: '',
    avatar: '',
    role: '',
    llmStyle: null,
    llmProvider: 'local',
    dailyTarget: 20
  }),
  actions: {
    setUserInfo(loginResponse) {
      const data = loginResponse || {}
      this.token = data.token || ''
      this.userId = data.userId ?? null
      this.username = data.username || ''
      this.nickname = data.nickname || ''
      this.avatar = data.avatar || ''
      this.role = data.role || ''
      this.llmStyle = data.llmStyle ?? null
      this.llmProvider = data.llmProvider || 'local'
      this.dailyTarget = data.dailyTarget ?? 20
      if (this.token) {
        setToken(this.token)
      }
    },
    clearUserInfo() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      this.role = ''
      this.llmStyle = null
      this.llmProvider = 'local'
      this.dailyTarget = 20
      removeToken()
    },
    async fetchUserInfo() {
      const res = await request.get('/user/info')
      if (res?.code === 200 && res.data) {
        this.userId = res.data.userId ?? this.userId
        this.username = res.data.username || ''
        this.nickname = res.data.nickname || ''
        this.avatar = res.data.avatar || ''
        this.role = res.data.role || ''
        this.llmStyle = res.data.llmStyle ?? null
        this.llmProvider = res.data.llmProvider || 'local'
        this.dailyTarget = res.data.dailyTarget ?? 20
      }
      return res
    }
  }
})