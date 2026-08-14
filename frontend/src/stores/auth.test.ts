import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    me: vi.fn(),
    changePassword: vi.fn()
  }
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('登录成功保存 token 与用户并持久化到 localStorage', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: 'jwt-token-1',
      user: { username: 'admin', role: 'ADMIN' }
    })
    const store = useAuthStore()
    await store.login('admin', 'admin123')

    expect(authApi.login).toHaveBeenCalledWith('admin', 'admin123')
    expect(store.token).toBe('jwt-token-1')
    expect(store.user).toEqual({ username: 'admin', role: 'ADMIN' })
    expect(store.isLoggedIn).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(localStorage.getItem('gateway-dashboard-token')).toBe('jwt-token-1')
    expect(localStorage.getItem('gateway-dashboard-user')).toContain('ADMIN')
  })

  it('登出清空状态与 localStorage', () => {
    localStorage.setItem('gateway-dashboard-token', 't0')
    localStorage.setItem('gateway-dashboard-user', JSON.stringify({ username: 'viewer', role: 'VIEWER' }))
    const store = useAuthStore()
    expect(store.isLoggedIn).toBe(true)

    store.logout()
    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(store.isLoggedIn).toBe(false)
    expect(localStorage.getItem('gateway-dashboard-token')).toBeNull()
  })

  it('刷新页面后从 localStorage 恢复登录态', () => {
    localStorage.setItem('gateway-dashboard-token', 'persisted-token')
    localStorage.setItem('gateway-dashboard-user', JSON.stringify({ username: 'viewer', role: 'VIEWER' }))

    const store = useAuthStore()
    expect(store.token).toBe('persisted-token')
    expect(store.role).toBe('VIEWER')
    expect(store.isAdmin).toBe(false)
    expect(store.roleLabel).toBe('只读用户')
  })
})
