import { get, post, put } from '@/api/http'
import type { LoginResponse, UserSummary } from '@/types'

export const authApi = {
  login: (username: string, password: string) =>
    post<LoginResponse>('/auth/login', { username, password }),
  me: () => get<UserSummary>('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    put<void>('/auth/password', { oldPassword, newPassword })
}
