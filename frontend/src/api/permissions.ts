import { del, get, post, put } from '@/api/http'
import type { PermissionRule, PermissionRuleRequest } from '@/types'

export const permissionsApi = {
  list: () => get<PermissionRule[]>('/permission-rules'),
  create: (data: PermissionRuleRequest) => post<PermissionRule>('/permission-rules', data),
  update: (id: number, data: PermissionRuleRequest) =>
    put<PermissionRule>(`/permission-rules/${id}`, data),
  remove: (id: number) => del<void>(`/permission-rules/${id}`)
}
