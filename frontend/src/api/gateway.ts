import { get } from '@/api/http'
import type { GatewayStatus } from '@/types'

export const gatewayApi = {
  status: () => get<GatewayStatus>('/gateway/status')
}
