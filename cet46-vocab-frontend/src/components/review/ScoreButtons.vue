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
  --score-btn-bg: #c8847e;
  --score-btn-text: #fffaf9;
  --score-btn-border: #ac6963;
  background: var(--score-btn-bg);
  color: var(--score-btn-text);
  border-color: var(--score-btn-border);
}

.btn-3 {
  --score-btn-bg: #c1a66a;
  --score-btn-text: #fffdf4;
  --score-btn-border: #a68e57;
  background: var(--score-btn-bg);
  color: var(--score-btn-text);
  border-color: var(--score-btn-border);
}

.btn-5 {
  --score-btn-bg: #5f8491;
  --score-btn-text: #f6fbfd;
  --score-btn-border: #4d707c;
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

/* Ensure manual theme toggle to dark mode is reflected immediately. */
:global(:root.dark) .btn-1 {
  --score-btn-bg: #8f5650;
  --score-btn-text: #fdeeed;
  --score-btn-border: #bc7f78;
}

:global(:root.dark) .btn-3 {
  --score-btn-bg: #92773e;
  --score-btn-text: #fff5dc;
  --score-btn-border: #bea264;
}

:global(:root.dark) .btn-5 {
  --score-btn-bg: #456977;
  --score-btn-text: #ecf8fc;
  --score-btn-border: #6b8f9e;
}

:global(:root.dark) .score-btn:hover:not(:disabled) {
  filter: brightness(1.06);
}

@media (max-width: 768px) {
  .score-row {
    grid-template-columns: 1fr;
  }
}
</style>

