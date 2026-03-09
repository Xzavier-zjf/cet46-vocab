<template>
  <header class="top-nav">
    <div class="brand">CET词库</div>

    <nav class="menu-scroll">
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

    <div class="actions">
      <button
        class="theme-toggle"
        type="button"
        :aria-label="isDark ? '切换为浅色模式' : '切换为深色模式'"
        @click="toggleTheme"
      >
        <el-icon>
          <component :is="isDark ? Sunny : Moon" />
        </el-icon>
      </button>

      <span class="nickname">{{ displayName }}</span>

      <el-dropdown trigger="click" @command="onCommand">
        <div class="user-trigger">
          <el-avatar :size="30" :src="userStore.avatar || undefined">{{ avatarText }}</el-avatar>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人资料</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  House,
  Collection,
  Reading,
  Refresh,
  Edit,
  TrendCharts,
  User,
  Upload,
  UserFilled,
  ChatDotRound,
  ArrowDown,
  Moon,
  Sunny
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
const MENU_ROUTE_MEMORY_KEY = 'menu:last-routes'

const displayName = computed(() => userStore.nickname || '同学')
const avatarText = computed(() => (displayName.value || '同').slice(0, 1))
const isDark = computed(() => themeStore.isDark)

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
    { label: '学习助手', path: '/assistant', icon: ChatDotRound },
    { label: '学习统计', path: '/stats', icon: TrendCharts },
    { label: '我的资料', path: '/profile', icon: User }
  ]
})

const toggleTheme = () => {
  themeStore.toggleTheme()
}

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

const onCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command !== 'logout') return

  try {
    if (userStore.token) {
      await logout()
    }
  } catch {
    ElMessage.warning('登出请求失败，已执行本地退出')
  } finally {
    userStore.clearUserInfo()
    router.push('/login')
  }
}
</script>

<style scoped>
.top-nav {
  height: 64px;
  position: sticky;
  top: 0;
  z-index: 1200;
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
  background: var(--color-nav-bg);
  border-bottom: 1px solid rgba(201, 168, 76, 0.4);
}

.brand {
  color: #f7f9fc;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.menu-scroll {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: hidden;
  padding-bottom: 2px;
}

.menu-scroll::-webkit-scrollbar {
  height: 4px;
}

.menu-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 8px;
}

.menu-item {
  height: 38px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  color: var(--color-nav-text);
  text-decoration: none;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.menu-item:hover {
  color: #ffffff;
  background: var(--color-nav-hover);
}

.menu-item.active {
  color: #fff9e8;
  background: var(--color-nav-active);
  box-shadow: inset 0 -2px 0 #c9a84c;
}

.menu-icon {
  font-size: 16px;
}

.actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.theme-toggle {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(217, 189, 115, 0.35);
  border-radius: 999px;
  background: rgba(14, 27, 49, 0.42);
  color: #f4e2b2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
  background: rgba(217, 189, 115, 0.16);
  border-color: rgba(217, 189, 115, 0.58);
}

.nickname {
  color: #e8edf6;
  font-size: 13px;
  white-space: nowrap;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-nav-text);
  cursor: pointer;
}

@media (max-width: 900px) {
  .top-nav {
    grid-template-columns: 120px minmax(0, 1fr);
  }

  .menu-scroll {
    overflow-x: auto;
  }

  .actions {
    display: none;
  }
}
</style>
