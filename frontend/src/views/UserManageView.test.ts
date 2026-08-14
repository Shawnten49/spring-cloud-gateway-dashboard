import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import UserManageView from '@/views/UserManageView.vue'
import { userAdminApi, type AdminUser } from '@/api/userAdmin'
import { elementStubs } from '@/test/elementStubs'

vi.mock('@/api/userAdmin', () => ({
  userAdminApi: { list: vi.fn(), create: vi.fn(), setEnabled: vi.fn() }
}))

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return {
    ...original,
    ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() }
  }
})

const adminUser: AdminUser = {
  id: 1,
  username: 'admin',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z'
}

const viewerUser: AdminUser = {
  id: 2,
  username: 'viewer',
  role: 'VIEWER',
  enabled: true,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z'
}

describe('UserManageView', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('gateway-dashboard-token', 't')
    localStorage.setItem('gateway-dashboard-user', JSON.stringify({ username: 'admin', role: 'ADMIN' }))
    vi.clearAllMocks()
    vi.mocked(userAdminApi.list).mockResolvedValue([adminUser, viewerUser])
  })

  function mountView() {
    return mount(UserManageView, {
      global: { plugins: [createPinia(), ElementPlus] }
    })
  }

  it('加载并渲染用户列表', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(userAdminApi.list).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('admin')
    expect(text).toContain('viewer')
  })

  it('admin 行屏蔽按钮禁用（特殊用户不可屏蔽）', async () => {
    const wrapper = mountView()
    await flushPromises()

    const allButtons = wrapper.findAll('button').filter((b) => b.text() === '屏蔽')
    const disabledButtons = allButtons.filter((b) => b.classes().includes('is-disabled'))
    const enabledButtons = allButtons.filter((b) => !b.classes().includes('is-disabled'))
    // admin 行按钮禁用（is-disabled）；viewer 行有一个可用屏蔽按钮
    expect(disabledButtons.length).toBe(1)
    expect(enabledButtons.length).toBe(1)
  })

  it('新建对话框：密码不足 8 位被拦截', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === '新建用户')!.trigger('click')
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('newuser')
    await inputs[1].setValue('short7x')
    await wrapper.findAll('button').find((b) => b.text() === '创建')!.trigger('click')
    await flushPromises()

    expect(userAdminApi.create).not.toHaveBeenCalled()
  })

  it('点击屏蔽调用 setEnabled 并刷新列表', async () => {
    vi.mocked(userAdminApi.setEnabled).mockResolvedValue({ ...viewerUser, enabled: false })
    const wrapper = mountView()
    await flushPromises()

    const disableButton = wrapper.findAll('button').find(
      (b) => b.text() === '屏蔽' && !b.classes().includes('is-disabled'))
    expect(disableButton).toBeTruthy()
    await disableButton!.trigger('click')
    await flushPromises()

    expect(userAdminApi.setEnabled).toHaveBeenCalledWith(viewerUser.id, false)
    expect(userAdminApi.list).toHaveBeenCalledTimes(2)
  })
})
