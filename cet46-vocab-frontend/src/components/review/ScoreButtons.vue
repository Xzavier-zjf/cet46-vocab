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
  color: var(--score-btn-text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease;
}

.score-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(0.96);
}

.score-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.btn-1 {
  --score-btn-bg: #8d5c56;
  --score-btn-text: #fdf8f7;
  --score-btn-border: #7d4f49;
  background: var(--score-btn-bg);
  color: var(--score-btn-text);
  border-color: var(--score-btn-border);
}

.btn-3 {
  --score-btn-bg: #a8863e;
  --score-btn-text: #fffaf0;
  --score-btn-border: #8f7337;
  background: var(--score-btn-bg);
  color: var(--score-btn-text);
  border-color: var(--score-btn-border);
}

.btn-5 {
  --score-btn-bg: #3f6371;
  --score-btn-text: #f3f8fa;
  --score-btn-border: #345461;
  background: var(--score-btn-bg);
  color: var(--score-btn-text);
  border-color: var(--score-btn-border);
}

.score-btn:disabled {
  background: var(--color-border);
  color: var(--color-muted);
  border-color: var(--color-border);
  cursor: not-allowed;
  transform: none;
}

@media (prefers-color-scheme: dark) {
  :global(:root.dark) .btn-1 {
    --score-btn-bg: #7a4e49;
    --score-btn-text: #f7e6e4;
    --score-btn-border: #9a6862;
  }

  :global(:root.dark) .btn-3 {
    --score-btn-bg: #8f7538;
    --score-btn-text: #fff3d8;
    --score-btn-border: #ad9152;
  }

  :global(:root.dark) .btn-5 {
    --score-btn-bg: #4c7382;
    --score-btn-text: #e9f6fb;
    --score-btn-border: #6a93a3;
  }
}

/* Ensure manual theme toggle to dark mode is reflected immediately. */
:global(:root.dark) .btn-1 {
  --score-btn-bg: #7a4e49;
  --score-btn-text: #f7e6e4;
  --score-btn-border: #9a6862;
}

:global(:root.dark) .btn-3 {
  --score-btn-bg: #8f7538;
  --score-btn-text: #fff3d8;
  --score-btn-border: #ad9152;
}

:global(:root.dark) .btn-5 {
  --score-btn-bg: #4c7382;
  --score-btn-text: #e9f6fb;
  --score-btn-border: #6a93a3;
}

@media (max-width: 768px) {
  .score-row {
    grid-template-columns: 1fr;
  }
}
</style>

