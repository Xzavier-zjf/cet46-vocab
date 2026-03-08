<template>
  <section class="word-detail">
    <section v-if="invalidParam" class="state-card">
      <h3>参数无效</h3>
      <p>请从词库列表重新进入该单词详情页。</p>
      <el-button class="back-btn" @click="goBackToWords">上一步</el-button>
    </section>

    <section v-else-if="loading" class="state-card">
      <el-skeleton animated :rows="6" />
    </section>

    <section v-else-if="emptyData" class="state-card">
      <h3>未找到该单词</h3>
      <p>该词条可能不存在或类型参数不匹配，请返回词库页重试。</p>
      <el-button class="back-btn" @click="goBackToWords">上一步</el-button>
    </section>

    <template v-else>
      <div class="top-row">
        <div>
          <el-button text class="back-link" @click="goBackToWords">上一步</el-button>
          <h1 class="word">{{ detail.english || '-' }}</h1>
          <div class="meta-inline">
            <span class="phonetic">{{ detail.phonetic || '' }}</span>
            <el-tag v-if="detail.pos" size="small" effect="plain">{{ detail.pos }}</el-tag>
          </div>
        </div>

        <div class="action-group">
          <el-button
            class="assistant-btn"
            :disabled="invalidParam || emptyData"
            @click="goAskAssistant"
          >
            问学习助手
          </el-button>
          <el-button
            class="learn-btn"
            :disabled="progressStatus !== 'NOT_LEARNING' || addLoading"
            :loading="addLoading"
            @click="handleAddLearn"
          >
            {{ statusText }}
          </el-button>
          <el-button
            class="retry-btn"
            :loading="retryLoading"
            :disabled="retryLoading || invalidParam || emptyData"
            @click="handleRetryGenerate"
          >
            重试AI生成
          </el-button>
        </div>
      </div>

      <section class="meaning-card">
        <h3>基础释义</h3>
        <p>{{ detail.chinese || '-' }}</p>
      </section>

      <WordMetaPanel
        :llm-content="detail.llmContent"
        :gen-status="displayGenStatus"
        :poll-stalled="pollStalled"
        @need-generate="handleNeedGenerate"
      />
    </template>
  </section>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import WordMetaPanel from '@/components/word/WordMetaPanel.vue'

const route = useRoute()
const router = useRouter()

const POLL_INTERVAL_MS = Number(import.meta.env.VITE_POLL_INTERVAL_MS || 3000)
const POLL_MAX_TIMES = Number(import.meta.env.VITE_POLL_MAX_TIMES || 30)
const AUTO_TRIGGER_COOLDOWN_MS = 8000

const detail = reactive({
  wordId: null,
  wordType: '',
  english: '',
  phonetic: '',
  chinese: '',
  pos: '',
  llmContent: {
    genStatus: 'pending',
    sentence: {},
    synonyms: [],
    mnemonic: {},
    smartExplain: '',
    grammarUsage: '',
    explainStatus: 'pending'
  },
  progress: {
    isLearning: false,
    status: 'NOT_LEARNING'
  }
})

const addLoading = ref(false)
const retryLoading = ref(false)
const pollStalled = ref(false)
const pollTimer = ref(null)
const pollCount = ref(0)
const loading = ref(false)
const invalidParam = ref(false)
const emptyData = ref(false)
const lastAutoTriggerAt = ref(0)
const explainPolling = ref(false)

const progressStatus = computed(() => detail.progress?.status || 'NOT_LEARNING')
const statusText = computed(() => {
  if (progressStatus.value === 'COMPLETED') return '已完成学习'
  if (progressStatus.value === 'LEARNING') return '学习中'
  return '加入学习'
})
const displayGenStatus = computed(() => detail.llmContent?.genStatus || 'pending')
const fromWordsRoute = computed(() => {
  const from = route.query.from
  if (typeof from !== 'string') return ''
  if (!from.startsWith('/words')) return ''
  return from
})

const validWordType = (type) => type === 'cet4' || type === 'cet6'
const validWordId = (id) => Number.isInteger(Number(id)) && Number(id) > 0

const stopPolling = () => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

const goBackToWords = () => {
  if (fromWordsRoute.value) {
    router.push(fromWordsRoute.value)
    return
  }
  if (window.history.length > 1) {
    router.go(-1)
    return
  }
  router.push('/words')
}

const goAskAssistant = () => {
  router.push({
    path: '/assistant',
    query: {
      from: route.fullPath,
      wordId: detail.wordId || '',
      wordType: detail.wordType || '',
      word: detail.english || '',
      phonetic: detail.phonetic || '',
      pos: detail.pos || '',
      chinese: detail.chinese || ''
    }
  })
}

const applyData = (data) => {
  detail.wordId = data?.wordId ?? null
  detail.wordType = data?.wordType || ''
  detail.english = data?.english || ''
  detail.phonetic = data?.phonetic || ''
  detail.chinese = data?.chinese || ''
  detail.pos = data?.pos || ''
  detail.llmContent = data?.llmContent || { genStatus: 'pending', sentence: {}, synonyms: [], mnemonic: {}, smartExplain: '', grammarUsage: '', explainStatus: 'pending' }
  detail.progress = data?.progress || { isLearning: false, status: 'NOT_LEARNING' }
}

const refreshProgressStatus = async () => {
  const res = await request.get('/word/progress/status', {
    params: {
      wordId: Number(route.params.id),
      wordType: route.params.type
    }
  })
  detail.progress = {
    ...(detail.progress || {}),
    isLearning: !!res?.data?.isLearning,
    status: res?.data?.status || 'NOT_LEARNING'
  }
}

const fetchDetail = async () => {
  const res = await request.get('/word/detail', {
    params: {
      wordId: Number(route.params.id),
      wordType: route.params.type
    }
  })
  applyData(res?.data || {})
  emptyData.value = !detail.english
  if (!emptyData.value) {
    await refreshProgressStatus()
  }
  return detail.llmContent?.genStatus || 'pending'
}

const hasIncompleteLlmContent = () => {
  const sentence = detail.llmContent?.sentence || {}
  const hasSentence = !!(sentence.sentenceEn || sentence.sentenceZh)
  const hasSynonyms = Array.isArray(detail.llmContent?.synonyms) && detail.llmContent.synonyms.length > 0
  const mnemonic = detail.llmContent?.mnemonic || {}
  const hasMnemonic = !!(mnemonic.mnemonic || mnemonic.rootAnalysis)
  return !(hasSentence && hasSynonyms && hasMnemonic)
}

const shouldKeepPolling = (status) => {
  const explainPending = explainPolling.value
    && !detail.llmContent?.smartExplain
    && (detail.llmContent?.explainStatus || 'pending') === 'pending'
  if (explainPending) return true
  if (status === 'pending') return true
  if (status === 'partial' && hasIncompleteLlmContent()) return true
  if (status === 'fallback' && hasIncompleteLlmContent()) return true
  return false
}

const startPollingIfNeeded = (status) => {
  stopPolling()
  if (!shouldKeepPolling(status)) return

  pollCount.value = 0
  pollTimer.value = setInterval(async () => {
    try {
      pollCount.value += 1
      const newStatus = await fetchDetail()
      if (!shouldKeepPolling(newStatus)) {
        stopPolling()
        pollStalled.value = false
        explainPolling.value = false
        return
      }
      if (pollCount.value >= POLL_MAX_TIMES) {
        if (!pollStalled.value) {
          pollStalled.value = true
          ElMessage.warning('AI仍在生成中，继续为你生成，请稍候')
        }
      }
    } catch {
      stopPolling()
    }
  }, POLL_INTERVAL_MS)
}

const initPage = async () => {
  stopPolling()
  pollStalled.value = false
  pollCount.value = 0
  explainPolling.value = false
  invalidParam.value = false
  emptyData.value = false

  const type = String(route.params.type || '').toLowerCase()
  const id = route.params.id
  if (!validWordType(type) || !validWordId(id)) {
    invalidParam.value = true
    return
  }

  loading.value = true
  try {
    const status = await fetchDetail()
    if (!emptyData.value) {
      startPollingIfNeeded(status)
    }
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '加载单词详情失败')
  } finally {
    loading.value = false
  }
}

const handleAddLearn = async () => {
  if (progressStatus.value !== 'NOT_LEARNING' || invalidParam.value || emptyData.value) return
  addLoading.value = true
  try {
    await request.post('/word/learn/add', {
      wordId: Number(route.params.id),
      wordType: route.params.type
    })
    await refreshProgressStatus()
    ElMessage.success('已加入学习计划')
  } finally {
    addLoading.value = false
  }
}

const handleRetryGenerate = async () => {
  if (retryLoading.value || invalidParam.value || emptyData.value) return
  const contentTaskOk = await triggerGenerateTask(true)
  if (contentTaskOk) {
    await triggerExplainTask(false)
  }
}

const triggerGenerateTask = async (showToast = false) => {
  if (retryLoading.value || invalidParam.value || emptyData.value) return false
  retryLoading.value = true
  try {
    const res = await request.post('/word/llm/generate', {
      wordId: Number(route.params.id),
      wordType: route.params.type
    })
    pollStalled.value = false
    detail.llmContent = {
      ...(detail.llmContent || {}),
      genStatus: 'pending'
    }
    startPollingIfNeeded('pending')
    if (showToast) {
      const provider = res?.data?.provider === 'cloud' ? '云端API' : '本地模型'
      ElMessage.success(`已提交AI重试任务（${provider}）`)
    }
    return true
  } catch (error) {
    if (!showToast) {
      ElMessage.warning(error?.businessMessage || error?.message || '自动触发AI生成失败，请手动重试')
    }
    return false
  } finally {
    retryLoading.value = false
  }
}

const handleNeedGenerate = async ({ section } = {}) => {
  if (invalidParam.value || emptyData.value) return
  const hasSynonyms = Array.isArray(detail.llmContent?.synonyms) && detail.llmContent.synonyms.length > 0
  const mnemonic = detail.llmContent?.mnemonic || {}
  const hasMnemonic = !!(mnemonic.mnemonic || mnemonic.rootAnalysis)
  const hasExplain = !!(detail.llmContent?.smartExplain)
  const hasGrammar = !!(detail.llmContent?.grammarUsage)
    || ((detail.llmContent?.smartExplain || '').includes('语法用法：'))
  if (section === 'synonym' && hasSynonyms) return
  if (section === 'mnemonic' && hasMnemonic) return
  if (section === 'explain' && hasExplain) return
  if (section === 'grammar' && hasGrammar) return

  const now = Date.now()
  if (now - lastAutoTriggerAt.value < AUTO_TRIGGER_COOLDOWN_MS) return
  lastAutoTriggerAt.value = now

  if (section === 'explain' || section === 'grammar') {
    await triggerExplainTask()
    return
  }

  if (displayGenStatus.value === 'pending') {
    startPollingIfNeeded('pending')
    return
  }
  await triggerGenerateTask(false)
}

const triggerExplainTask = async (showWarn = true) => {
  try {
    await request.post('/word/llm/generate-explain', {
      wordId: Number(route.params.id),
      wordType: route.params.type
    })
    explainPolling.value = true
    detail.llmContent = {
      ...(detail.llmContent || {}),
      explainStatus: 'pending'
    }
    startPollingIfNeeded('pending')
  } catch (error) {
    if (showWarn) {
      ElMessage.warning(error?.businessMessage || error?.message || '智能解释生成失败，请稍后重试')
    }
  }
}

watch(
  () => [route.params.type, route.params.id],
  async () => {
    await initPage()
  },
  { immediate: true }
)

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.word-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.state-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 22px;
}

.state-card h3 {
  margin: 0 0 10px;
  color: #1a2b4a;
}

.state-card p {
  margin: 0;
  color: #6b7a8d;
}

.back-btn {
  margin-top: 12px;
}

.top-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.word {
  margin: 0;
  color: #1a2b4a;
  font-size: 44px;
  line-height: 1.1;
  font-weight: 700;
}

.back-link {
  padding: 0;
  margin-bottom: 8px;
  color: #6d7f95;
}

.meta-inline {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.phonetic {
  color: #8896a8;
  font-size: 18px;
}

.learn-btn {
  background: #1a2b4a;
  border-color: #1a2b4a;
  color: #fff;
}

.learn-btn:disabled {
  background: #d0d7e3;
  border-color: #d0d7e3;
  color: #f8f9fb;
}

.retry-btn {
  border-color: #1a2b4a;
  color: #1a2b4a;
}

.assistant-btn {
  border-color: #c9a84c;
  color: #6c5311;
  background: #fffdf6;
}

.meaning-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 18px 20px;
}

.meaning-card h3 {
  margin: 0 0 10px;
  color: #1a2b4a;
  font-size: 16px;
}

.meaning-card p {
  margin: 0;
  color: #2c3e50;
  line-height: 1.8;
}

@media (max-width: 768px) {
  .top-row {
    flex-direction: column;
  }

  .action-group {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .word {
    font-size: 36px;
  }
}
</style>
