<template>
  <aside class="sidebar">
    <div class="logo">CET词库</div>

    <nav class="menu">
      <RouterLink
        v-for="item in menuItems"
        :key="item.path"
        :to="resolveMenuPath(item.path)"
        class="menu-item"
        :class="{ active: isActive(item) }"
      >
        <el-icon class="menu-icon">
          <component :is="item.icon" />
        </el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="footer">
      <el-button class="logout-btn" text @click="handleLogout">退出登录</el-button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { House, Collection, Reading, Refresh, Edit, TrendCharts, User, Upload, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const MENU_ROUTE_MEMORY_KEY = 'menu:last-routes'

const menuItems = computed(() => {
  if (userStore.role === 'ADMIN') {
    return [
      { label: '词库导入', path: '/admin', icon: Upload, exact: true },
      { label: '用户管理', path: '/admin/users', icon: UserFilled, exact: false },
      { label: '我的资料', path: '/profile', icon: User, exact: false }
    ]
  }

  return [
    { label: '学习仪表盘', path: '/dashboard', icon: House },
    { label: '今日学习', path: '/learn', icon: Reading },
    { label: '词库浏览', path: '/words', icon: Collection },
    { label: '开始复习', path: '/review', icon: Refresh },
    { label: '模拟测验', path: '/quiz', icon: Edit },
    { label: '学习统计', path: '/stats', icon: TrendCharts },
    { label: '我的资料', path: '/profile', icon: User }
  ]
})

const isActive = (item) => {
  if (item.exact) {
    return route.path === item.path
  }
  return route.path === item.path || route.path.startsWith(`${item.path}/`)
}

const resolveMenuPath = (path) => {
  try {
    const memory = JSON.parse(sessionStorage.getItem(MENU_ROUTE_MEMORY_KEY) || '{}')
    const remembered = memory?.[path]
    return remembered || path
  } catch {
    return path
  }
}

const handleLogout = async () => {
  try {
    if (userStore.token) {
      await logout()
    }
  } catch {
    ElMessage.warning('退出请求失败，已执行本地退出')
  } finally {
    userStore.clearUserInfo()
    router.push('/login')
  }
}
</script>

<style scoped>
.sidebar {
  width: 220px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a2b4a;
  color: #fff;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  position: sticky;
  top: 0;
  flex-shrink: 0;
  overflow-y: auto;
}

.logo {
  height: 68px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.menu {
  flex: 1;
  padding: 12px 0;
}

.menu-item {
  position: relative;
  height: 48px;
  margin: 2px 10px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 10px;
  color: #fff;
  text-decoration: none;
  transition: background-color 0.2s ease;
}

.menu-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.menu-item.active {
  background: rgba(201, 168, 76, 0.15);
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 0;
  width: 4px;
  height: 100%;
  background: #c9a84c;
  border-radius: 0 4px 4px 0;
}

.menu-icon {
  font-size: 18px;
}

.footer {
  padding: 16px 14px 20px;
}

.logout-btn {
  width: 100%;
  justify-content: center;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 10px;
  height: 40px;
}

.logout-btn:hover {
  color: #fff;
  border-color: #c9a84c;
  background: rgba(201, 168, 76, 0.15);
}
</style>