import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('@/views/Register.vue') },
  { path: '/onboarding', name: 'Onboarding', component: () => import('@/views/Onboarding.vue'), meta: { requiresAuth: true } },
  { path: '/dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { requiresAuth: true } },
  { path: '/words', name: 'WordList', component: () => import('@/views/WordList.vue'), meta: { requiresAuth: true } },
  { path: '/words/:type/:id', name: 'WordDetail', component: () => import('@/views/WordDetail.vue'), meta: { requiresAuth: true } },
  { path: '/review', name: 'Review', component: () => import('@/views/Review.vue'), meta: { requiresAuth: true } },
  { path: '/quiz', name: 'Quiz', component: () => import('@/views/Quiz.vue'), meta: { requiresAuth: true } },
  { path: '/stats', name: 'Statistics', component: () => import('@/views/Statistics.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboard.vue'), meta: { requiresAuth: true, role: 'ADMIN' } },
  { path: '/', redirect: '/dashboard' },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const hasToken = !!userStore.token
  const requiresAuth = !!to.meta.requiresAuth

  if (!hasToken && requiresAuth) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  if (hasToken && to.path === '/login') {
    next('/dashboard')
    return
  }

  if (hasToken && !userStore.userId) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      userStore.clearUserInfo()
      next('/login')
      return
    }
  }

  if (hasToken && requiresAuth && userStore.llmStyle == null && to.path !== '/onboarding') {
    next('/onboarding')
    return
  }

  if (to.meta.role && userStore.role !== to.meta.role) {
    next('/dashboard')
    return
  }

  next()
})

export default router
