<template>
  <div class="score-row">
    <button
      class="score-btn btn-1"
      :disabled="disabled"
      @click="$emit('score', 1)"
    >
      完全陌生
    </button>
    <button
      class="score-btn btn-3"
      :disabled="disabled"
      @click="$emit('score', 3)"
    >
      有点模糊
    </button>
    <button
      class="score-btn btn-5"
      :disabled="disabled"
      @click="$emit('score', 5)"
    >
      轻松掌握
    </button>
  </div>
</template>

<script setup>
defineProps({
  disabled: { type: Boolean, default: false }
})

defineEmits(['score'])
</script>

<style scoped>
.score-row {
  width: 100%;
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.score-btn {
  height: 44px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease;
}

.score-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(0.96);
}

.btn-1 {
  background: var(--color-primary);
  border-color: rgba(255, 255, 255, 0.18);
}

.btn-3 {
  background: var(--color-warning);
  border-color: rgba(255, 255, 255, 0.18);
}

.btn-5 {
  background: var(--color-accent);
  color: #1a2b4a;
  border-color: rgba(26, 43, 74, 0.24);
}

.score-btn:disabled {
  background: var(--color-border);
  color: var(--color-muted);
  border-color: var(--color-border);
  cursor: not-allowed;
  transform: none;
}

@media (max-width: 768px) {
  .score-row {
    grid-template-columns: 1fr;
  }
}
</style>

