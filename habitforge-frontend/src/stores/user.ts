import { defineStore } from 'pinia'
import { apiLogin, apiRegister, apiLogout, apiMe } from '@/api'
import type { LoginRequest, RegisterRequest, UserProfile } from '@/types/user'
import { setToken, clearToken } from '@/utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    user: null as UserProfile | null
  }),
  getters: {
    isLoggedIn: (s) => !!s.token
  },
  actions: {
    async login(form: LoginRequest) {
      const res = await apiLogin(form)
      this.token = res.token
      this.user = res.user
      setToken(res.token)
    },
    async register(form: RegisterRequest) {
      const res = await apiRegister(form)
      this.token = res.token
      this.user = res.user
      setToken(res.token)
    },
    async fetchMe() {
      this.user = await apiMe()
    },
    async logout() {
      try {
        await apiLogout()
      } finally {
        this.token = ''
        this.user = null
        clearToken()
      }
    }
  },
  persist: {
    key: 'habitforge-user',
    paths: ['token', 'user']
  }
})
