import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PermissionRuleView from '@/views/PermissionRuleView.vue'
import { permissionsApi } from '@/api/permissions'
import { elementStubs } from '@/test/elementStubs'
import type { PermissionRule } from '@/types'

vi.mock('@/api/permissions', () => ({
  permissionsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() }
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

const builtinRule: PermissionRule = {
  id: 1,
  name: '权限配置-查看',
  httpMethod: 'GET',
  pathPattern: '/api/permission-rules/**',
  roles: 'ADMIN',
  priority: 5,
  enabled: true,
  builtin: true,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z'
}

describe('PermissionRuleView', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('gateway-dashboard-token', 't')
    localStorage.setItem('gateway-dashboard-user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    vi.clearAllMocks()
    vi.mocked(permissionsApi.list).mockResolvedValue([builtinRule])
  })

  function mountView() {
    return mount(PermissionRuleView, {
      global: { plugins: [createPinia()], stubs: elementStubs, directives: { loading: {} } }
    })
  }

  it('挂载时加载规则列表', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(permissionsApi.list).toHaveBeenCalledTimes(1)
    // 规则列标签渲染（stub 的 el-table-column 渲染 label）
    expect(wrapper.text()).toContain('规则名称')
    expect(wrapper.text()).toContain('新建规则')
  })

  it('打开新建对话框后可保存新规则', async () => {
    vi.mocked(permissionsApi.create).mockResolvedValue({ ...builtinRule, id: 99, builtin: false })
    const wrapper = mountView()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((b) => b.text() === '新建规则')
    await createButton!.trigger('click')
    await flushPromises()

    // 对话框内填写表单（el-input stub 的输入框按顺序：名称/路径/角色）
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('路由-新增')
    await inputs[1].setValue('/api/routes/**')
    await inputs[2].setValue('ADMIN')

    const saveButton = wrapper.findAll('button').find((b) => b.text() === '保存并生效')
    expect(saveButton).toBeTruthy()
    await saveButton!.trigger('click')
    await flushPromises()

    expect(permissionsApi.create).toHaveBeenCalledTimes(1)
    expect(permissionsApi.list).toHaveBeenCalledTimes(2)
  })

  it('内置规则删除按钮不渲染（不可删除）', async () => {
    const wrapper = mountView()
    await flushPromises()

    // 内置规则所在行的"删除"按钮不应存在（v-if="!row.builtin"）
    const deleteButtons = wrapper.findAll('button').filter((b) => b.text() === '删除')
    expect(deleteButtons.length).toBe(0)
  })
})
