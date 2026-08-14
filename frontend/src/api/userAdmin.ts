import { get, post, put } from '@/api/http'
import type { UserSummary } from '@/types'

export interface AdminUser extends UserSummary {
  id: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  role: string
}

export const userAdminApi = {
  list: (keyword?: string) => get<AdminUser[]>('/users', keyword ? { keyword } : undefined),
  create: (data: CreateUserRequest) => post<AdminUser>('/users', data),
  setEnabled: (id: number, enabled: boolean) =>
    put<AdminUser>(`/users/${id}/enabled`, { enabled })
}
