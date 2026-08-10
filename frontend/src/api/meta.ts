import { get } from '@/api/http'

export const metaApi = {
  factories: (type: 'predicate' | 'filter') =>
    get<Record<string, string[]>>('/meta/factories', { type })
}
