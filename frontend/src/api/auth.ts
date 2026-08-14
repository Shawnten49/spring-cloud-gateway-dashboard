import { post, put } from '@/api/http'
import type { LoginResponse } from '@/types'

export const authApi = {
  login: (username: string, password: string) =>
    post<LoginResponse>('/auth/login', { username, password }),
  changePassword: (oldPassword: string, newPassword: string) =>
    put<void>('/auth/password', { oldPassword, newPassword })
}
