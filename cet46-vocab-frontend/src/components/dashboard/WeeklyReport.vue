<template>
  <section class="report-card">
    <h4 class="title">📊 本周学习报告</h4>
    <p class="content">{{ report || '本周报告正在生成中。' }}</p>
  </section>
</template>

<script setup>
defineProps({
  report: { type: String, default: '' }
})
</script>

<style scoped>
.report-card {
  background: transparent;
  padding: 8px 4px;
}

.title {
  margin: 0 0 12px;
  color: #c9a84c;
  font-size: 16px;
  font-weight: 700;
}

.content {
  margin: 0;
  color: #8896a8;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
