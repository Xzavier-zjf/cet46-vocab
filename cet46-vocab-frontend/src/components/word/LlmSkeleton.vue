<template>
  <div class="llm-skeleton">
    <el-skeleton animated :rows="3" />
    <p class="hint">AI 正在为你编织记忆点...</p>
  </div>
</template>

<script setup>
</script>

<style scoped>
.llm-skeleton {
  padding: 12px 0 2px;
}

.hint {
  margin: 14px 0 0;
  text-align: center;
  color: #8896A8;
  font-size: 14px;
  animation: blink 1.6s ease-in-out infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
</style>
