import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'

const MENU_ROUTE_MEMORY_KEY = 'menu:last-routes'
const MENU_ROOTS = ['/dashboard', '/learn', '/words', '/review', '/quiz', '/assistant', '/stats', '/profile', '/admin/users', '/admin/permissions', '/admin']

const getMenuRoot = (path) => MENU_ROOTS.find((root) => path === root || path.startsWith(`${root}/`))

const loadMenuRouteMemory = () => {
  try {
    return JSON.parse(sessionStorage.getItem(MENU_ROUTE_MEMORY_KEY) || '{}')
  } catch {
    return {}
  }
}

const saveMenuRouteMemory = (memory) => {
  sessionStorage.setItem(MENU_ROUTE_MEMORY_KEY, JSON.stringify(memory))
}

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/register', name: 'Register', component: () => import('@/views/Register.vue') },
  { path: '/onboarding', name: 'Onboarding', component: () => import('@/views/Onboarding.vue'), meta: { requiresAuth: true } },
  { path: '/dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { requiresAuth: true } },
  { path: '/learn', name: 'Learn', component: () => import('@/views/Learn.vue'), meta: { requiresAuth: true } },
  { path: '/words', name: 'WordList', component: () => import('@/views/WordList.vue'), meta: { requiresAuth: true } },
  { path: '/words/:type/:id', name: 'WordDetail', component: () => import('@/views/WordDetail.vue'), meta: { requiresAuth: true } },
  { path: '/review', name: 'Review', component: () => import('@/views/Review.vue'), meta: { requiresAuth: true } },
  { path: '/quiz', name: 'Quiz', component: () => import('@/views/Quiz.vue'), meta: { requiresAuth: true } },
  { path: '/assistant', name: 'Assistant', component: () => import('@/views/Assistant.vue'), meta: { requiresAuth: true } },
  { path: '/stats', name: 'Statistics', component: () => import('@/views/Statistics.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboard.vue'), meta: { requiresAuth: true, role: 'ADMIN' } },
  { path: '/admin/users', name: 'AdminUsers', component: () => import('@/views/admin/UserManagement.vue'), meta: { requiresAuth: true, role: 'ADMIN' } },
  { path: '/admin/permissions', name: 'AdminPermissions', component: () => import('@/views/admin/RolePermissionManagement.vue'), meta: { requiresAuth: true, role: 'ADMIN' } },
  { path: '/', redirect: '/dashboard' },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const DYNAMIC_IMPORT_RELOAD_FLAG = 'vite:dynamic-import-reloaded'

router.onError((error) => {
  const message = String(error?.message || '')
  const isDynamicImportError =
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed')

  if (!isDynamicImportError) {
    return
  }

  const alreadyReloaded = sessionStorage.getItem(DYNAMIC_IMPORT_RELOAD_FLAG) === '1'
  if (alreadyReloaded) {
    sessionStorage.removeItem(DYNAMIC_IMPORT_RELOAD_FLAG)
    return
  }

  sessionStorage.setItem(DYNAMIC_IMPORT_RELOAD_FLAG, '1')
  window.location.reload()
})

router.beforeEach(async (to, from, next) => {
  if (sessionStorage.getItem(DYNAMIC_IMPORT_RELOAD_FLAG) === '1') {
    sessionStorage.removeItem(DYNAMIC_IMPORT_RELOAD_FLAG)
  }

  const userStore = useUserStore()
  const themeStore = useThemeStore()
  const hasToken = !!userStore.token
  const requiresAuth = !!to.meta.requiresAuth

  if (!hasToken && themeStore.activeIdentity) {
    themeStore.setActiveIdentity('')
  }

  if (!hasToken && requiresAuth) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  if (hasToken && to.path === '/login') {
    next(userStore.role === 'ADMIN' ? '/admin' : '/dashboard')
    return
  }

  if (hasToken && !userStore.userId) {
    try {
      await userStore.fetchUserInfo()
      themeStore.setActiveIdentity(userStore.userId || userStore.username || '')
    } catch (error) {
      userStore.clearUserInfo()
      next('/login')
      return
    }
  }

  if (hasToken && userStore.userId) {
    themeStore.setActiveIdentity(userStore.userId || userStore.username || '')
  }

  if (hasToken && requiresAuth && userStore.llmStyle == null && to.path !== '/onboarding') {
    next('/onboarding')
    return
  }

  if (to.meta.role && userStore.role !== to.meta.role) {
    next('/dashboard')
    return
  }

  if (hasToken && userStore.role === 'ADMIN') {
    const allowList = ['/admin', '/admin/users', '/admin/permissions', '/profile', '/onboarding']
    if (!allowList.some((path) => to.path === path || to.path.startsWith(`${path}/`))) {
      next('/admin')
      return
    }
  }

  next()
})

router.afterEach((to) => {
  const root = getMenuRoot(to.path)
  if (!root) return
  const memory = loadMenuRouteMemory()
  memory[root] = to.fullPath
  saveMenuRouteMemory(memory)
})

export default router

