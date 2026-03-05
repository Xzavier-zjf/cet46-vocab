<template>
  <div v-if="show" class="alert" :class="levelClass">
    {{ message }}
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  pressureIndex: { type: Number, default: 0 },
  pressureAlert: { type: Boolean, default: false }
})

const show = computed(() => props.pressureIndex > 100)

const levelClass = computed(() => {
  if (props.pressureAlert || props.pressureIndex > 150) return 'high'
  return 'mid'
})

const message = computed(() => {
  if (props.pressureAlert || props.pressureIndex > 150) {
    return '建议今日暂停新词学习，专注消灭存量'
  }
  return '今日复习压力较高，注意节奏'
})
</script>

<style scoped>
.alert {
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
}

.alert.mid {
  background: #f8f0d9;
  color: #7a6522;
  border: 1px solid #ead9a8;
}

.alert.high {
  background: #c9a84c;
  color: #1a2b4a;
  border: 1px solid #c9a84c;
}
</style>
