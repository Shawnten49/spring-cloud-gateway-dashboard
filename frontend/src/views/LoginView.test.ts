import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from '@/views/LoginView.vue'
import { authApi } from '@/api/auth'
import { elementStubs } from '@/test/elementStubs'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

vi.mock('@/api/auth', () => ({
  authApi: { login: vi.fn(), me: vi.fn(), changePassword: vi.fn() }
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() }
}))

describe('LoginView', () => {
  beforeEach(() => {
    push.mockClear()
    vi.clearAllMocks()
  })

  function mountView() {
    return mount(LoginView, {
      global: { plugins: [createPinia()], stubs: elementStubs }
    })
  }
  it('空表单提交不调用登录接口', async () => {
    const wrapper = mountView()
    await wrapper.find('button').trigger('click')
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('填写凭据后登录成功并跳转路由', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 't',
      user: { username: 'admin', role: 'ADMIN' }
    })
    const wrapper = mountView()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('admin123')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(authApi.login).toHaveBeenCalledWith('admin', 'admin123')
    expect(push).toHaveBeenCalledWith({ name: 'routes' })
  })

  it('登录失败不跳转', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('401'))
    const wrapper = mountView()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('wrong')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(authApi.login).toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
  })
})
