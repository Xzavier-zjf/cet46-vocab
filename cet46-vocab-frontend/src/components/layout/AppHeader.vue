<template>
  <header class="app-header">
    <div class="header-right">
      <button
        class="theme-toggle"
        type="button"
        :aria-label="isDark ? '切换为浅色模式' : '切换为深色模式'"
        @click="toggleTheme"
      >
        <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon>
      </button>
      <span class="nickname">{{ displayName }}</span>
      <el-dropdown trigger="click" @command="onCommand">
        <div class="user-trigger">
          <el-avatar :size="32" :src="userStore.avatar || undefined">{{ avatarText }}</el-avatar>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Moon, Sunny } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { logout } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const displayName = computed(() => userStore.nickname || userStore.username || '同学')
const avatarText = computed(() => (displayName.value || '同').slice(0, 1))
const isDark = computed(() => themeStore.isDark)
const toggleTheme = () => themeStore.toggleTheme()

const onCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
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
}
</script>

<style scoped>
.app-header {
  height: 60px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.theme-toggle {
  width: 34px;
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-soft);
  color: var(--color-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease, background-color 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-1px);
}

.nickname {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-muted);
}

.user-trigger:focus-visible {
  outline: none;
}
</style>
