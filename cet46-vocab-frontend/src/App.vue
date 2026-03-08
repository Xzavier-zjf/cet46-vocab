<template>
  <router-view v-if="isFullPageRoute" />

  <div
    v-else
    class="app-layout"
    :class="{ 'academic-theme': !isStatsRoute, 'stats-keep-blue': isStatsRoute }"
  >
    <AppSidebar />
    <main class="page-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const route = useRoute()
const fullPageRoutes = ['/login', '/register', '/onboarding']

const isFullPageRoute = computed(() => fullPageRoutes.includes(route.path))
const isStatsRoute = computed(() => route.path === '/stats' || route.path.startsWith('/stats/'))
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: grid;
  grid-template-rows: 64px 1fr;
  background: var(--color-bg);
}

.app-layout.academic-theme {
  --el-color-primary: #c9a84c;
  --el-color-primary-light-3: #d7bb71;
  --el-color-primary-light-5: #e2cb95;
  --el-color-primary-light-7: #ecdcba;
  --el-color-primary-light-8: #f1e6cc;
  --el-color-primary-light-9: #f8f2e5;
  --el-color-primary-dark-2: #b79434;
}

.app-layout.stats-keep-blue {
  --el-color-primary: #409eff;
}

.page-content {
  padding: 24px;
  min-width: 0;
  overflow: auto;
}
</style>
