import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import GatewayStatusView from '@/views/GatewayStatusView.vue'
import { gatewayApi } from '@/api/gateway'
import type { GatewayStatus } from '@/types'

vi.mock('@/api/gateway', () => ({
  gatewayApi: { status: vi.fn() }
}))

const statusFixture: GatewayStatus = {
  health: 'UP',
  lastRefreshAt: '2026-08-15T00:00:00Z',
  effectiveRoutes: [
    {
      routeId: 'httpbin-get',
      uri: 'http://httpbin.org',
      order: 0,
      enabled: true,
      predicates: [{ name: 'Path', args: { patterns: '/get' } }],
      filters: [{ name: 'AddRequestHeader', args: { name: 'X-Demo', value: '1' } }],
      metadata: {},
      version: 1,
      updatedAt: '2026-08-15T00:00:00Z'
    }
  ],
  externalGateways: []
}

describe('GatewayStatusView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(gatewayApi.status).mockResolvedValue(statusFixture)
  })

  function mountView() {
    return mount(GatewayStatusView, {
      global: { plugins: [createPinia(), ElementPlus] }
    })
  }

  it('加载后展示健康状态与生效路由的 Predicates/Filters 完整 JSON 串', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(gatewayApi.status).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('正常')
    // 完整 JSON 串（含 args），而非仅 name 列表
    expect(text).toContain('{"name":"Path","args":{"patterns":"/get"}}')
    expect(text).toContain('{"name":"AddRequestHeader","args":{"name":"X-Demo","value":"1"}}')
    // 单元格使用省略样式类（供 el-tooltip 悬浮展示完整内容）
    expect(wrapper.find('.cell-ellipsis').exists()).toBe(true)
  })
})
