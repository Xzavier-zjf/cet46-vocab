<template>
  <section class="stats-page">
    <section class="report-card">
      <h3>本周学习报告</h3>
      <p>{{ weeklyReport || '本周学习报告正在生成中。' }}</p>
    </section>

    <section class="panel">
      <h4>近30天每日复习量</h4>
      <LineChart :data="dailyCount" />
    </section>

    <div class="two-col">
      <section class="panel">
        <h4>词性分布</h4>
        <BarChart :data="posDistribution" />
      </section>

      <section class="panel">
        <h4>学习热力图（近一年）</h4>
        <HeatmapCalendar :data="heatmapData" />
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview, getStats } from '@/api/dashboard'
import LineChart from '@/components/charts/LineChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import HeatmapCalendar from '@/components/charts/HeatmapCalendar.vue'

const overview = ref(null)
const stats = ref(null)

const weeklyReport = computed(() => overview.value?.weeklyReport || '')
const dailyCount = computed(() => (Array.isArray(stats.value?.dailyCount) ? stats.value.dailyCount : []))
const posDistribution = computed(() => (Array.isArray(stats.value?.posDistribution) ? stats.value.posDistribution : []))
const heatmapData = computed(() => (Array.isArray(stats.value?.heatmap) ? stats.value.heatmap : []))

onMounted(async () => {
  const [overviewRes, statsRes] = await Promise.all([
    getOverview(),
    getStats(30)
  ])
  overview.value = overviewRes?.data || null
  stats.value = statsRes?.data || null
})
</script>

<style scoped>
.stats-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-card,
.panel {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 18px 20px;
}

.report-card h3,
.panel h4 {
  margin: 0 0 12px;
  color: #1A2B4A;
}

.report-card p {
  margin: 0;
  color: #8896A8;
  line-height: 1.8;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1024px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
