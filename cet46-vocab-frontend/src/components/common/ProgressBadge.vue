<template>
  <span class="progress-badge" :class="`is-${meta.tone}`">
    <span class="badge-icon" aria-hidden="true">{{ meta.icon }}</span>
    <span class="badge-label">{{ meta.label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { getProgressMeta } from '@/constants/wordProgress'

const props = defineProps({
  status: {
    type: String,
    default: 'NOT_LEARNING'
  }
})

const meta = computed(() => getProgressMeta(String(props.status || '').toUpperCase()))
</script>

<style scoped>
.progress-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.3;
  border: 1px solid transparent;
  font-weight: 700;
}

.badge-icon {
  font-size: 11px;
}

.progress-badge.is-learning {
  color: var(--color-primary-strong);
  background: color-mix(in srgb, var(--color-primary-strong) 10%, transparent);
  border-color: color-mix(in srgb, var(--color-primary-strong) 35%, transparent);
}

.progress-badge.is-completed {
  color: var(--color-success);
  background: var(--color-success-soft);
  border-color: color-mix(in srgb, var(--color-success) 35%, transparent);
}

.progress-badge.is-pending {
  color: var(--color-muted-strong);
  background: var(--color-surface-soft);
  border-color: var(--color-border-soft);
}
</style>
