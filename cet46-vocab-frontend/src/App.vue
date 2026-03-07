<template>
  <router-view v-if="isFullPageRoute" />

  <div v-else class="app-layout">
    <AppSidebar />
    <div class="main-layout">
      <AppHeader />
      <main class="page-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppHeader from '@/components/layout/AppHeader.vue'

const route = useRoute()
const fullPageRoutes = ['/login', '/register', '/onboarding']

const isFullPageRoute = computed(() => fullPageRoutes.includes(route.path))
</script>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
  background: var(--color-bg);
  overflow: hidden;
}

.main-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}

.page-content {
  flex: 1;
  padding: 24px;
  min-width: 0;
  overflow: auto;
}
</style>
