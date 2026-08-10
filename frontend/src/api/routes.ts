import { del, get, post, put } from '@/api/http'
import type { RouteConfig, RouteRequest, ValidationResult } from '@/types'

export const routesApi = {
  list: (keyword?: string) =>
    get<RouteConfig[]>('/routes', keyword ? { keyword } : undefined),
  get: (routeId: string) => get<RouteConfig>(`/routes/${routeId}`),
  create: (data: RouteRequest) => post<RouteConfig>('/routes', data),
  update: (routeId: string, data: RouteRequest) =>
    put<RouteConfig>(`/routes/${routeId}`, data),
  remove: (routeId: string) => del<void>(`/routes/${routeId}`),
  setEnabled: (routeId: string, enabled: boolean) =>
    post<RouteConfig>(`/routes/${routeId}/enabled`, { enabled }),
  validate: (data: RouteRequest) => post<ValidationResult>('/routes/validate', data)
}
