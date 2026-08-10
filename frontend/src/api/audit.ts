import { get } from '@/api/http'
import type { AuditLog, PageResult } from '@/types'

export const auditApi = {
  page: (page: number, size: number) =>
    get<PageResult<AuditLog>>('/audit-logs', { page, size })
}
