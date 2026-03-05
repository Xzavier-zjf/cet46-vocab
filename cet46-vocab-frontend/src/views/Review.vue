<template>
  <section class="review-page">
    <div v-if="isLoading" class="state-panel center">
      <el-icon class="loading-icon is-loading"><Loading /></el-icon>
    </div>

    <div v-else-if="isEmpty" class="state-panel center">
      <el-icon class="empty-icon"><CircleCheck /></el-icon>
      <h3>今日词汇已全部复习完成</h3>
      <p>本次完成 {{ reviewStore.sessionStats.reviewed }} / {{ reviewStore.sessionStats.total }}</p>
    </div>

    <div v-else-if="isCardFlow" class="card-flow">
      <ProgressBar
        :reviewed="reviewStore.sessionStats.reviewed"
        :total="reviewStore.sessionStats.total"
      />

      <FlipCard
        :word="reviewStore.currentWord"
        :is-flipped="reviewStore.isFlipped"
        @flip="onFlip"
      />

      <div v-if="showScoreButtons" class="score-area">
        <ScoreButtons :disabled="false" @score="onScore" />
      </div>

      <div v-if="isSubmitting" class="score-area submitting">
        <ScoreButtons :disabled="true" />
        <div class="submit-tip">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>提交中...</span>
        </div>
      </div>

      <div v-if="isError" class="error-area">
        <el-button type="primary" :color="'#1A2B4A'" @click="onRetry">
          重试提交
        </el-button>
      </div>
    </div>

    <div v-else-if="isComplete" class="state-panel complete">
      <h2>今日复习完成</h2>
      <div class="summary-grid">
        <div class="summary-card">
          <span class="label">今日复习数</span>
          <strong>{{ reviewStore.sessionStats.reviewed }}</strong>
        </div>
        <div class="summary-card">
          <span class="label">平均用时</span>
          <strong>{{ avgSpentMs }} ms</strong>
        </div>
        <div class="summary-card">
          <span class="label">本次分布</span>
          <strong>1分 {{ dist.one }} / 3分 {{ dist.three }} / 5分 {{ dist.five }}</strong>
        </div>
      </div>
      <el-button class="restart-btn" @click="restartSession">再复习一轮</el-button>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Loading } from '@element-plus/icons-vue'
import { useReviewStore } from '@/stores/review'
import ProgressBar from '@/components/review/ProgressBar.vue'
import FlipCard from '@/components/review/FlipCard.vue'
import ScoreButtons from '@/components/review/ScoreButtons.vue'

const reviewStore = useReviewStore()
const cardStartAt = ref(0)
const lastFailedPayload = ref(null)
const spentMsList = ref([])

const isLoading = computed(() => reviewStore.sessionState === 'loading')
const isEmpty = computed(() => reviewStore.sessionState === 'empty')
const isSubmitting = computed(() => reviewStore.sessionState === 'submitting')
const isError = computed(() => reviewStore.sessionState === 'error')
const isComplete = computed(() => reviewStore.sessionState === 'complete')
const isCardFlow = computed(() =>
  ['card_front', 'card_back', 'submitting', 'error'].includes(reviewStore.sessionState)
)
const showScoreButtons = computed(() => reviewStore.sessionState === 'card_back')

const dist = computed(() => {
  const stats = { one: 0, three: 0, five: 0 }
  reviewStore.sessionStats.scores.forEach((score) => {
    if (score === 1) stats.one += 1
    if (score === 3) stats.three += 1
    if (score === 5) stats.five += 1
  })
  return stats
})

const avgSpentMs = computed(() => {
  if (!spentMsList.value.length) return 0
  const total = spentMsList.value.reduce((sum, t) => sum + t, 0)
  return Math.round(total / spentMsList.value.length)
})

const setCardStart = () => {
  cardStartAt.value = Date.now()
}

const onFlip = () => {
  reviewStore.flipCard()
}

const onScore = async (score) => {
  const spent = Math.max(Date.now() - cardStartAt.value, 0)
  const success = await reviewStore.submitScore(score, spent)
  if (success) {
    spentMsList.value.push(spent)
    lastFailedPayload.value = null
    setCardStart()
    return
  }
  lastFailedPayload.value = { score, timeSpentMs: spent }
}

const onRetry = async () => {
  if (!lastFailedPayload.value) return
  const { score, timeSpentMs } = lastFailedPayload.value
  const success = await reviewStore.retrySubmit(score, timeSpentMs)
  if (success) {
    spentMsList.value.push(timeSpentMs)
    lastFailedPayload.value = null
    setCardStart()
  }
}

const restartSession = async () => {
  spentMsList.value = []
  lastFailedPayload.value = null
  await reviewStore.startSession()
  setCardStart()
}

watch(
  () => reviewStore.submitError,
  (msg) => {
    if (msg) {
      ElMessage.error(msg)
    }
  }
)

onMounted(async () => {
  spentMsList.value = []
  lastFailedPayload.value = null
  await reviewStore.startSession()
  setCardStart()
})

onUnmounted(() => {
  reviewStore.resetSession()
})
</script>

<style scoped>
.review-page {
  max-width: 980px;
  margin: 0 auto;
  min-height: calc(100vh - 108px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.state-panel {
  width: 100%;
  min-height: 320px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  padding: 36px;
}

.center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.loading-icon {
  font-size: 34px;
  color: #1A2B4A;
}

.empty-icon {
  font-size: 42px;
  color: #C9A84C;
}

.center h3 {
  margin: 14px 0 8px;
  color: #1A2B4A;
}

.center p {
  margin: 0;
  color: #6C7B8E;
}

.card-flow {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.score-area {
  display: flex;
  justify-content: center;
}

.score-area.submitting {
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.submit-tip {
  color: #6C7B8E;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.error-area {
  display: flex;
  justify-content: center;
}

.complete h2 {
  margin: 0 0 18px;
  color: #1A2B4A;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  background: #F8FAFD;
  border: 1px solid #E0E6ED;
  border-radius: 12px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-card .label {
  color: #6C7B8E;
  font-size: 13px;
}

.summary-card strong {
  color: #1A2B4A;
  font-size: 20px;
}

.restart-btn {
  margin-top: 18px;
  background: #1A2B4A;
  color: #fff;
  border: 0;
}

.restart-btn:hover {
  filter: brightness(0.95);
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
