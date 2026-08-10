export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface UserSummary {
  username: string
  role: string
}

export interface LoginResponse {
  token: string
  user: UserSummary
}

export interface Step {
  name: string
  args: Record<string, unknown>
}

export interface RouteConfig {
  routeId: string
  uri: string
  order: number
  enabled: boolean
  predicates: Step[]
  filters: Step[]
  metadata: Record<string, unknown>
  version: number
  updatedAt: string
}

export interface RouteRequest {
  routeId: string
  uri: string
  order: number
  enabled: boolean
  predicates: Step[]
  filters: Step[]
  metadata: Record<string, unknown>
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

export interface GatewayStatus {
  health: string
  lastRefreshAt: string | null
  effectiveRoutes: RouteConfig[]
  externalGateways: ExternalGatewayStatus[]
}

export interface RouteSummary {
  routeId: string
  uri: string
  order: number
}

export interface PushInfo {
  lastPushAt: string | null
  success: boolean
  error: string | null
}

export interface ExternalGatewayStatus {
  baseUrl: string
  online: boolean
  push: PushInfo | null
  lastCheckedAt: string
  effectiveRoutes: RouteSummary[]
  error: string | null
}

export interface AuditLog {
  id: number
  actorUsername: string
  action: string
  routeId: string | null
  beforeJson: string | null
  afterJson: string | null
  ip: string | null
  createdAt: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PermissionRule {
  id: number
  name: string
  httpMethod: string
  pathPattern: string
  roles: string
  priority: number
  enabled: boolean
  builtin: boolean
  createdAt: string
  updatedAt: string
}

export interface PermissionRuleRequest {
  name: string
  httpMethod: string
  pathPattern: string
  roles: string
  priority: number
  enabled: boolean
}
