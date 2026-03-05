<template>
  <section class="dashboard-page">
    <template v-if="loading">
      <div class="overview-grid">
        <article v-for="idx in 4" :key="`skeleton-metric-${idx}`" class="metric-card">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="text" style="width: 42%; height: 14px;" />
              <el-skeleton-item variant="text" style="width: 66%; height: 34px; margin-top: 14px;" />
            </template>
          </el-skeleton>
        </article>
      </div>
      <div class="middle-panel">
        <section class="chart-card">
          <el-skeleton animated :rows="6" />
        </section>
        <section class="report-box">
          <el-skeleton animated :rows="8" />
        </section>
      </div>
    </template>

    <template v-else>
      <div class="overview-grid">
        <article class="metric-card">
          <span class="label">待复习</span>
          <strong>{{ overview.todayDue }}</strong>
        </article>
        <article class="metric-card">
          <span class="label">连续天数</span>
          <strong>{{ overview.streakDays }}</strong>
        </article>
        <article class="metric-card">
          <span class="label">已掌握</span>
          <strong>{{ overview.masteredCount }}</strong>
        </article>
        <article class="metric-card">
          <span class="label">总学习</span>
          <strong>{{ overview.totalLearned }}</strong>
        </article>
      </div>

      <PressureAlert
        :pressure-index="overview.pressureIndex"
        :pressure-alert="overview.pressureAlert"
      />

      <div class="middle-panel">
        <section class="chart-card">
          <h3>学习掌握分布</h3>
          <RingChart
            :mastered="overview.masteredCount"
            :learning="learningCount"
            :total="chartTotal"
          />
        </section>

        <section class="report-box">
          <WeeklyReport :report="overview.weeklyReport" />
        </section>
      </div>

      <div class="quick-actions">
        <el-button class="quick-btn primary" @click="go('/review')">开始复习</el-button>
        <el-button class="quick-btn" @click="go('/words')">浏览词库</el-button>
        <el-button class="quick-btn" @click="go('/quiz')">模拟测验</el-button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDashboardStore } from '@/stores/dashboard'
import PressureAlert from '@/components/dashboard/PressureAlert.vue'
import RingChart from '@/components/dashboard/RingChart.vue'
import WeeklyReport from '@/components/dashboard/WeeklyReport.vue'

const router = useRouter()
const dashboardStore = useDashboardStore()
const loading = ref(true)

const overview = computed(() => ({
  todayDue: dashboardStore.overview?.todayDue ?? 0,
  streakDays: dashboardStore.overview?.streakDays ?? 0,
  masteredCount: dashboardStore.overview?.masteredCount ?? 0,
  totalLearned: dashboardStore.overview?.totalLearned ?? 0,
  pressureIndex: dashboardStore.overview?.pressureIndex ?? 0,
  pressureAlert: dashboardStore.overview?.pressureAlert ?? false,
  weeklyReport: dashboardStore.overview?.weeklyReport ?? ''
}))

const learningCount = computed(() => {
  const value = overview.value.totalLearned - overview.value.masteredCount
  return value > 0 ? value : 0
})

const chartTotal = computed(() => {
  const value = overview.value.totalLearned
  return value > 0 ? value : 1
})

const go = (path) => router.push(path)

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([
      dashboardStore.fetchOverview(),
      dashboardStore.fetchStats(30)
    ])
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px 18px;
}

.label {
  display: block;
  color: #8896a8;
  font-size: 13px;
  margin-bottom: 10px;
}

.metric-card strong {
  color: #1a2b4a;
  font-size: 28px;
  font-weight: 700;
}

.middle-panel {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.chart-card,
.report-box {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px;
}

.chart-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.chart-card h3 {
  margin: 0;
  color: #1a2b4a;
  font-size: 16px;
}

.quick-actions {
  display: flex;
  gap: 12px;
}

.quick-btn {
  min-width: 124px;
  height: 42px;
  border-radius: 10px;
  border-color: var(--color-border);
}

.quick-btn.primary {
  background: #1a2b4a;
  color: #fff;
  border-color: #1a2b4a;
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .middle-panel {
    grid-template-columns: 1fr;
  }

  .chart-card {
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    flex-direction: column;
  }
}
</style>
