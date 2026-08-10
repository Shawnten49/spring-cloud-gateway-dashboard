import { describe, expect, it } from 'vitest'
import { normalizeRequest, parseRequestJson, validateRequestClient } from './routeJson'

describe('routeJson utils', () => {
  it('normalizes a raw parsed object into RouteRequest', () => {
    const request = normalizeRequest({
      routeId: 'demo',
      uri: 'http://example.com',
      order: '3',
      enabled: true,
      predicates: [{ name: 'Path', args: { patterns: '/x' } }],
      filters: [],
      metadata: { owner: 'me' }
    })
    expect(request.routeId).toBe('demo')
    expect(request.order).toBe(3)
    expect(request.predicates[0].name).toBe('Path')
  })

  it('parses valid JSON text and keeps arrays', () => {
    const text = JSON.stringify({
      routeId: 'a',
      uri: 'http://a',
      order: 1,
      enabled: true,
      predicates: [{ name: 'Path', args: { patterns: '/a' } }],
      filters: [],
      metadata: {}
    })
    const request = parseRequestJson(text)
    expect(request.routeId).toBe('a')
    expect(request.predicates.length).toBe(1)
  })

  it('rejects malformed JSON', () => {
    expect(() => parseRequestJson('{oops')).toThrow('JSON 格式错误')
  })

  it('reports client-side validation errors', () => {
    expect(validateRequestClient({ routeId: '', uri: '', order: 0, enabled: true, predicates: [], filters: [], metadata: {} })).toHaveLength(3)
    expect(validateRequestClient({ routeId: 'ok', uri: 'http://ok', order: 0, enabled: true, predicates: [{ name: 'Path', args: {} }], filters: [], metadata: {} })).toHaveLength(0)
  })
})
