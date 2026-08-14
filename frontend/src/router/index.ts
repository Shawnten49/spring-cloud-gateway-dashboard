import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', redirect: '/routes' },
    { path: '/routes', name: 'routes', component: () => import('@/views/RouteListView.vue'), meta: { title: '路由管理' } },
    { path: '/gateway', name: 'gateway', component: () => import('@/views/GatewayStatusView.vue'), meta: { title: '网关状态' } },
    { path: '/audit', name: 'audit', component: () => import('@/views/AuditLogView.vue'), meta: { title: '操作日志' } },
    { path: '/permissions', name: 'permissions', component: () => import('@/views/PermissionRuleView.vue'), meta: { title: '权限配置', requiresAdmin: true } },
    { path: '/users', name: 'users', component: () => import('@/views/UserManageView.vue'), meta: { title: '用户管理', requiresAdmin: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.name !== 'login' && !auth.isLoggedIn) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'routes' }
  }
  // 角色守卫：requiresAdmin 页面仅 ADMIN 可达（VIEWER 直接访问被重定向回路由管理）
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'routes' }
  }
  return true
})

export default router
