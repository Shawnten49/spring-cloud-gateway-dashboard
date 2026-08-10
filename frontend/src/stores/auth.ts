import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { UserSummary } from '@/types'

const TOKEN_KEY = 'gateway-dashboard-token'
const USER_KEY = 'gateway-dashboard-user'

function readUser(): UserSummary | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserSummary | null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    roleLabel: (state) =>
      state.user?.role === 'ADMIN' ? '管理员' : state.user?.role === 'VIEWER' ? '只读用户' : state.user?.role || ''
  },
  actions: {
    async login(username: string, password: string) {
      const res = await authApi.login(username, password)
      this.token = res.token
      this.user = res.user
      localStorage.setItem(TOKEN_KEY, res.token)
      localStorage.setItem(USER_KEY, JSON.stringify(res.user))
    },
    async fetchMe() {
      this.user = await authApi.me()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    async changePassword(oldPassword: string, newPassword: string) {
      await authApi.changePassword(oldPassword, newPassword)
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
