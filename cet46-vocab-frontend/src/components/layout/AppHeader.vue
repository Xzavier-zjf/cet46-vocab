<template>
  <header class="app-header">
    <div class="header-right">
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
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const displayName = computed(() => userStore.nickname || '同学')
const avatarText = computed(() => (displayName.value || '同').slice(0, 1))

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
    } catch (error) {
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
  background: #fff;
  border-bottom: 1px solid #E0E6ED;
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
  color: #667285;
}

.user-trigger:focus-visible {
  outline: none;
}
</style>
