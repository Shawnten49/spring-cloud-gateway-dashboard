import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import RouteListView from '@/views/RouteListView.vue'
import { routesApi } from '@/api/routes'
import { metaApi } from '@/api/meta'
import type { RouteConfig } from '@/types'

vi.mock('@/api/routes', () => ({
  routesApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    setEnabled: vi.fn(),
    validate: vi.fn()
  }
}))

vi.mock('@/api/meta', () => ({
  metaApi: { factories: vi.fn() }
}))

vi.mock('@/components/RouteEditorDrawer.vue', () => ({
  default: { name: 'RouteEditorDrawer', template: '<div />' }
}))

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return {
    ...original,
    ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
  }
})

const sampleRoute = (overrides: Partial<RouteConfig> = {}): RouteConfig => ({
  routeId: 'httpbin-get',
  uri: 'http://httpbin.org',
  order: 0,
  enabled: true,
  predicates: [{ name: 'Path', args: { patterns: '/get' } }],
  filters: [],
  metadata: {},
  version: 1,
  updatedAt: '2026-08-01T00:00:00Z',
  ...overrides
})

describe('RouteListView', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('gateway-dashboard-token', 't')
    localStorage.setItem('gateway-dashboard-user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    vi.clearAllMocks()
    vi.mocked(routesApi.list).mockResolvedValue([sampleRoute()])
    vi.mocked(metaApi.factories).mockResolvedValue({ predicate: ['Path'], filter: ['AddRequestHeader'] })
  })

  function mountView() {
    // 使用真实 Element Plus 渲染表格（含行内操作按钮），浏览器 API 由 src/test/setup.ts stub
    return mount(RouteListView, {
      global: {
        plugins: [createPinia(), ElementPlus],
        stubs: { RouteEditorDrawer: true }
      }
    })
  }

  it('挂载时加载路由列表与工厂', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(routesApi.list).toHaveBeenCalledTimes(1)
    expect(metaApi.factories).toHaveBeenCalledWith('predicate')
    expect(metaApi.factories).toHaveBeenCalledWith('filter')
    // 管理员可见"新建路由"按钮
    expect(wrapper.text()).toContain('新建路由')
  })

  it('输入关键词后点查询会携带关键词重新加载', async () => {
    const wrapper = mountView()
    await flushPromises()

    const input = wrapper.find('input')
    await input.setValue('httpbin')
    const queryButton = wrapper.findAll('button').find((b) => b.text() === '查询')
    expect(queryButton).toBeTruthy()
    await queryButton!.trigger('click')
    await flushPromises()

    expect(routesApi.list).toHaveBeenLastCalledWith('httpbin')
  })

  it('点击停用调用 setEnabled 并刷新列表', async () => {
    vi.mocked(routesApi.setEnabled).mockResolvedValue(sampleRoute({ enabled: false }))
    const wrapper = mountView()
    await flushPromises()

    const disableButton = wrapper.findAll('button').find((b) => b.text() === '停用')
    expect(disableButton).toBeTruthy()
    await disableButton!.trigger('click')
    await flushPromises()

    expect(routesApi.setEnabled).toHaveBeenCalledWith('httpbin-get', false)
    expect(routesApi.list).toHaveBeenCalledTimes(2)
  })
})
