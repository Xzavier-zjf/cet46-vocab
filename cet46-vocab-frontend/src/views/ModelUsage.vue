<template>
  <section class="usage-page">
    <section class="usage-shell">
      <header class="usage-hero">
        <div>
          <p class="usage-eyebrow">Cloud Model Usage</p>
          <h1>{{ pageTitle }}</h1>
          <p class="usage-subtitle">{{ pageSubtitle }}</p>
        </div>
        <BtnSecondary :loading="loading" @click="loadUsage">刷新统计</BtnSecondary>
      </header>

      <section class="summary-grid">
        <article v-for="card in summaryCards" :key="card.label" class="summary-card">
          <span class="summary-label">{{ card.label }}</span>
          <strong class="summary-value">{{ card.value }}</strong>
          <small class="summary-hint">{{ card.hint }}</small>
        </article>
      </section>

      <section class="panel-grid">
        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>近 7 天调用趋势</h2>
              <p>只统计云端模型调用，管理员视图仅汇总公有模型。</p>
            </div>
          </div>
          <LineChart v-if="showTrendChart" :data="trendChartData" y-axis-name="调用次数" />
          <el-empty v-else description="暂无调用记录" :image-size="92" />
        </article>

        <article class="panel note-panel">
          <div class="panel-head">
            <div>
              <h2>计费说明</h2>
              <p>当前实现为简化版，重点保证统计链路和权限隔离正确。</p>
            </div>
          </div>
          <ul class="note-list">
            <li>{{ summary.billingNote || '成本统计为简化版，未接入平台单价。' }}</li>
            <li>{{ summary.quotaNote || '免费模型额度请以云平台控制台为准。' }}</li>
            <li v-if="isAdmin">管理员页只展示所有用户对公有云端模型的使用情况。</li>
            <li v-else>用户页展示你自己的公有云端模型与个人私有云端模型调用情况。</li>
          </ul>
        </article>
      </section>

      <article class="panel">
        <div class="panel-head">
          <div>
            <h2>{{ isAdmin ? '模型使用排行' : '我的模型明细' }}</h2>
            <p>{{ isAdmin ? '按 30 天调用次数排序。' : '支持查看公有 / 私有模型调用差异。' }}</p>
          </div>
        </div>
        <el-table :data="models" size="small" class="usage-table" empty-text="暂无模型使用记录">
          <el-table-column label="模型" min-width="220">
            <template #default="{ row }">
              <div class="model-name-cell">
                <strong>{{ row.displayName || row.modelKey }}</strong>
                <span>{{ row.modelKey }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="提供商" width="120">
            <template #default="{ row }">{{ providerLabel(row.provider) }}</template>
          </el-table-column>
          <el-table-column label="范围" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="scopeTagType(row.scope)" effect="plain">{{ scopeLabel(row.scope) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="callsToday" label="今日" width="90" />
          <el-table-column prop="calls7d" label="7天" width="90" />
          <el-table-column prop="calls30d" label="30天" width="100" />
          <el-table-column label="成本 / 额度" min-width="260">
            <template #default="{ row }">
              <div class="billing-cell">
                <span :class="['billing-pill', row.freeTier ? 'is-free' : '']">{{ row.costLabel }}</span>
                <small>{{ row.quotaLabel }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近调用" min-width="170">
            <template #default="{ row }">{{ formatTimestamp(row.lastUsedAt) }}</template>
          </el-table-column>
        </el-table>
      </article>

      <article v-if="isAdmin" class="panel">
        <div class="panel-head">
          <div>
            <h2>用户使用明细</h2>
            <p>只包含公有云端模型调用，按 30 天调用次数排序。</p>
          </div>
        </div>
        <el-table :data="users" size="small" class="usage-table" empty-text="暂无用户使用记录">
          <el-table-column label="用户" min-width="180">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.nickname || row.username || `用户 ${row.userId}` }}</strong>
                <span>ID: {{ row.userId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="220">
            <template #default="{ row }">
              <div class="model-name-cell compact">
                <strong>{{ row.displayName || row.modelKey }}</strong>
                <span>{{ row.modelKey }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="提供商" width="120">
            <template #default="{ row }">{{ providerLabel(row.provider) }}</template>
          </el-table-column>
          <el-table-column prop="callsToday" label="今日" width="90" />
          <el-table-column prop="calls7d" label="7天" width="90" />
          <el-table-column prop="calls30d" label="30天" width="100" />
          <el-table-column label="成本 / 额度" min-width="260">
            <template #default="{ row }">
              <div class="billing-cell">
                <span class="billing-pill">{{ row.costLabel }}</span>
                <small>{{ row.quotaLabel }}</small>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import BtnSecondary from '@/components/common/BtnSecondary.vue'
import LineChart from '@/components/charts/LineChart.vue'

const userStore = useUserStore()
const loading = ref(false)
const usage = ref({
  viewRole: '',
  summary: {},
  trend: [],
  models: [],
  users: []
})

const isAdmin = computed(() => userStore.role === 'ADMIN')
const summary = computed(() => usage.value?.summary || {})
const models = computed(() => Array.isArray(usage.value?.models) ? usage.value.models : [])
const users = computed(() => Array.isArray(usage.value?.users) ? usage.value.users : [])

const pageTitle = computed(() => isAdmin.value ? '模型用量总览' : '我的模型用量')
const pageSubtitle = computed(() => isAdmin.value
  ? '查看所有用户对公有云端模型的调用活跃度与简化成本提示。'
  : '查看你自己的公有云端模型与私有云端模型调用情况。')

const toSafeInteger = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.max(0, Math.round(num))
}

const trendChartData = computed(() => {
  const source = Array.isArray(usage.value?.trend) ? usage.value.trend : []
  return source.map((item) => ({
    date: shortDate(item.date),
    count: toSafeInteger(item.calls)
  }))
})

const showTrendChart = computed(() => trendChartData.value.length > 0)

const summaryCards = computed(() => {
  const cards = [
    {
      label: '今日调用',
      value: formatCount(summary.value.totalCallsToday),
      hint: '当天云端模型请求次数'
    },
    {
      label: '近 7 天',
      value: formatCount(summary.value.totalCalls7d),
      hint: '滚动 7 天累计调用'
    },
    {
      label: '近 30 天',
      value: formatCount(summary.value.totalCalls30d),
      hint: '滚动 30 天累计调用'
    },
    {
      label: isAdmin.value ? '活跃用户' : '活跃模型',
      value: isAdmin.value ? formatCount(summary.value.activeUsers) : formatCount(summary.value.activeModels),
      hint: isAdmin.value ? '30 天内有公有模型调用的用户数' : '30 天内你调用过的模型数'
    }
  ]
  cards.push({
    label: '公有模型调用',
    value: formatCount(summary.value.publicCalls30d),
    hint: '近 30 天公有模型调用次数'
  })
  if (!isAdmin.value) {
    cards.push({
      label: '私有模型调用',
      value: formatCount(summary.value.privateCalls30d),
      hint: '近 30 天私有模型调用次数'
    })
  }
  return cards
})

const loadUsage = async () => {
  loading.value = true
  try {
    const url = isAdmin.value ? '/admin/llm/usage' : '/user/llm/usage'
    const res = await request.get(url, { timeout: 15000 })
    usage.value = res?.data || {
      viewRole: isAdmin.value ? 'ADMIN' : 'USER',
      summary: {},
      trend: [],
      models: [],
      users: []
    }
  } finally {
    loading.value = false
  }
}

const providerLabel = (provider) => {
  const value = String(provider || '').trim().toLowerCase()
  if (!value) return '未标记'
  if (value === 'cloud') return '云端 API'
  return value
}

const scopeLabel = (scope) => String(scope || '').trim().toLowerCase() === 'private' ? '私有' : '公有'
const scopeTagType = (scope) => String(scope || '').trim().toLowerCase() === 'private' ? 'warning' : 'success'

const shortDate = (value) => {
  const text = String(value || '').trim()
  if (text.length !== 8) return text || '--'
  return `${text.slice(4, 6)}-${text.slice(6, 8)}`
}

const formatTimestamp = (value) => {
  const ts = Number(value)
  if (!Number.isFinite(ts) || ts <= 0) return '--'
  const date = new Date(ts)
  if (Number.isNaN(date.getTime())) return '--'
  return date.toLocaleString()
}

const formatCount = (value) => `${toSafeInteger(value)}`

defineExpose({ usage, summary, models, users, summaryCards, loadUsage })

onMounted(async () => {
  if (!userStore.role) {
    await userStore.fetchUserInfo()
  }
  await loadUsage()
})
</script>

<style scoped>
.usage-page {
  display: flex;
  justify-content: center;
}

.usage-shell {
  width: min(1180px, 100%);
  display: grid;
  gap: 18px;
}

.usage-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 24px 26px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(201, 168, 76, 0.18), transparent 34%),
    linear-gradient(135deg, var(--color-surface), var(--color-surface-soft));
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
}

.usage-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.usage-hero h1 {
  margin: 0;
  color: var(--color-primary-strong);
  font-size: 30px;
}

.usage-subtitle {
  margin: 10px 0 0;
  max-width: 720px;
  color: var(--color-muted-strong);
  line-height: 1.7;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.panel {
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.summary-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background:
    linear-gradient(180deg, rgba(201, 168, 76, 0.06), transparent 55%),
    var(--color-surface);
}

.summary-label {
  color: var(--color-muted);
  font-size: 12px;
}

.summary-value {
  color: var(--color-primary-strong);
  font-size: 28px;
  line-height: 1;
}

.summary-hint {
  color: var(--color-muted-soft);
  line-height: 1.5;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  color: var(--color-primary-strong);
  font-size: 18px;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.note-list {
  margin: 0;
  padding-left: 18px;
  color: var(--color-muted-strong);
  line-height: 1.8;
}

.usage-table :deep(.el-table__cell) {
  background: transparent;
}

.model-name-cell,
.user-cell,
.billing-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.model-name-cell strong,
.user-cell strong {
  color: var(--color-primary-strong);
}

.model-name-cell span,
.user-cell span,
.billing-cell small {
  color: var(--color-muted);
  line-height: 1.5;
}

.billing-pill {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(26, 43, 74, 0.08);
  color: var(--color-primary-strong);
  font-size: 12px;
  font-weight: 600;
}

.billing-pill.is-free {
  background: rgba(201, 168, 76, 0.14);
  color: #8b6914;
}

:global(:root.dark) .billing-pill.is-free {
  color: #f0d98f;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .usage-hero,
  .panel-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .usage-hero {
    padding: 20px;
  }

  .usage-hero h1 {
    font-size: 24px;
  }
}
</style>







