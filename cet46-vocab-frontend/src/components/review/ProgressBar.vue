<template>
  <div class="progress-shell">
    <div class="progress-fill" :style="{ width: `${progress}%` }" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  reviewed: { type: Number, default: 0 },
  total: { type: Number, default: 0 }
})

const progress = computed(() => {
  if (!props.total || props.total <= 0) return 0
  return Math.min(100, Math.round((props.reviewed / props.total) * 100))
})
</script>

<style scoped>
.progress-shell {
  width: 100%;
  height: 4px;
  background: rgba(26, 43, 74, 0.12);
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  width: 0;
  background: #1A2B4A;
  transition: width 0.3s ease;
}
</style>
