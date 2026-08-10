import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', redirect: '/routes' },
    { path: '/routes', name: 'routes', component: () => import('@/views/RouteListView.vue'), meta: { title: '路由管理' } },
    { path: '/gateway', name: 'gateway', component: () => import('@/views/GatewayStatusView.vue'), meta: { title: '网关状态' } },
    { path: '/audit', name: 'audit', component: () => import('@/views/AuditLogView.vue'), meta: { title: '操作日志' } }
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
  return true
})

export default router
