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
      手到擒来
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
  border: 0;
  border-radius: 8px;
  color: #fff;
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
  background: #1A2B4A;
}

.btn-3 {
  background: #4A6FA5;
}

.btn-5 {
  background: #8BAFD4;
}

.score-btn:disabled {
  background: #C7CED8;
  color: #f7f9fc;
  cursor: not-allowed;
  transform: none;
}

@media (max-width: 768px) {
  .score-row {
    grid-template-columns: 1fr;
  }
}
</style>
