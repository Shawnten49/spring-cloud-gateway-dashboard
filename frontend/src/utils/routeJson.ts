import type { RouteRequest, Step } from '@/types'

export function toRequestJson(request: RouteRequest): string {
  return JSON.stringify(request, null, 2)
}

export function parseRequestJson(text: string): RouteRequest {
  let obj: unknown
  try {
    obj = JSON.parse(text)
  } catch (e) {
    throw new Error('JSON 格式错误：' + (e as Error).message)
  }
  return normalizeRequest(obj)
}

export function normalizeRequest(obj: unknown): RouteRequest {
  const raw = (obj ?? {}) as Record<string, unknown>
  const steps = (value: unknown): Step[] => {
    if (!Array.isArray(value)) return []
    return value.map((item) => {
      const step = (item ?? {}) as Record<string, unknown>
      return {
        name: String(step.name ?? ''),
        args: (step.args as Record<string, unknown>) ?? {}
      }
    })
  }
  return {
    routeId: String(raw.routeId ?? ''),
    uri: String(raw.uri ?? ''),
    order: typeof raw.order === 'number' ? raw.order : Number(raw.order ?? 0),
    enabled: raw.enabled !== false,
    predicates: steps(raw.predicates),
    filters: steps(raw.filters),
    metadata: (raw.metadata as Record<string, unknown>) ?? {}
  }
}

export function validateRequestClient(request: RouteRequest): string[] {
  const errors: string[] = []
  if (!request.routeId?.trim()) errors.push('路由 ID 不能为空')
  else if (!/^[A-Za-z0-9_.-]{1,128}$/.test(request.routeId || '')) {
    errors.push('路由 ID 只能包含字母、数字、点、下划线、连字符')
  }
  if (!request.uri?.trim()) errors.push('目标地址不能为空')
  if (request.enabled && (!request.predicates || request.predicates.length === 0)) {
    errors.push('启用状态的路由至少需要一个 predicate')
  }
  return errors
}
