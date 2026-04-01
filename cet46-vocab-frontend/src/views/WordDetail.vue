<template>
  <section class="word-detail">
    <section v-if="invalidParam" class="state-card">
      <h3>参数无效</h3>
      <p>请从词库列表重新进入该单词详情页。</p>
      <BtnSecondary class="back-btn" @click="goBackToWords">返回</BtnSecondary>
    </section>

    <section v-else-if="loading" class="state-card">
      <el-skeleton animated :rows="6" />
    </section>

    <section v-else-if="emptyData" class="state-card">
      <h3>未找到该单词</h3>
      <p>该词条可能不存在或类型参数不匹配，请返回词库页重试。</p>
      <BtnSecondary class="back-btn" @click="goBackToWords">返回</BtnSecondary>
    </section>

    <template v-else>
      <div class="top-row">
        <div>
          <el-button text class="back-link" @click="goBackToWords">返回</el-button>
          <h1 class="word">{{ detail.english || '-' }}</h1>
          <div class="meta-inline">
            <span class="phonetic">{{ detail.phonetic || '' }}</span>
            <el-button text size="small" class="speak-btn" @click="handleSpeak('uk')">🔊 英音</el-button>
            <el-button text size="small" class="speak-btn" @click="handleSpeak('us')">🔊 美音</el-button>
            <el-tag v-if="detail.pos" size="small" effect="plain">{{ detail.pos }}</el-tag>
          </div>
        </div>

        <div class="action-group">
          <ProgressBadge :status="progressStatus" />
          <BtnSecondary
            class="assistant-btn"
            :disabled="invalidParam || emptyData"
            @click="goAskAssistant"
          >
            问学习助手
          </BtnSecondary>
          <BtnPrimary
            class="learn-btn"
            :disabled="progressStatus !== 'NOT_LEARNING' || addLoading"
            :loading="addLoading"
            @click="handleAddLearn"
          >
            {{ statusText }}
          </BtnPrimary>
          <BtnSecondary
            class="retry-btn"
            :loading="retryLoading"
            :disabled="retryLoading || invalidParam || emptyData"
            @click="handleRetryGenerate"
          >
            重试AI生成
          </BtnSecondary>
        </div>
      </div>

      <section class="meaning-card">
        <h3>基础释义</h3>
        <p>{{ detail.chinese || '-' }}</p>
      </section>

      <section class="item-status-card">
        <h3>Item Status</h3>
        <div class="item-status-grid">
          <span>Sentence: {{ sectionStatusLabel(sentenceSectionStatus) }}</span>
          <span>Synonym: {{ sectionStatusLabel(synonymSectionStatus) }}</span>
          <span>Mnemonic: {{ sectionStatusLabel(mnemonicSectionStatus) }}</span>
        </div>
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
import { speakWord } from '@/utils/speech'
import { WORD_PROGRESS, getProgressMeta } from '@/constants/wordProgress'
import ProgressBadge from '@/components/common/ProgressBadge.vue'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

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
  if (progressStatus.value === WORD_PROGRESS.COMPLETED) return '已完成学习'
  if (progressStatus.value === WORD_PROGRESS.LEARNING) return getProgressMeta(progressStatus.value).label
  return '加入学习'
})
const displayGenStatus = computed(() => detail.llmContent?.genStatus || 'pending')
const sentenceSectionStatus = computed(() => normalizeSectionStatus(detail.llmContent?.sentenceStatus, !!(detail.llmContent?.sentence?.sentenceEn || detail.llmContent?.sentence?.sentenceZh), displayGenStatus.value))
const synonymSectionStatus = computed(() => normalizeSectionStatus(detail.llmContent?.synonymStatus, Array.isArray(detail.llmContent?.synonyms) && detail.llmContent.synonyms.length > 0, displayGenStatus.value))
const mnemonicSectionStatus = computed(() => normalizeSectionStatus(detail.llmContent?.mnemonicStatus, !!(detail.llmContent?.mnemonic?.mnemonic || detail.llmContent?.mnemonic?.rootAnalysis), displayGenStatus.value))
const fromWordsRoute = computed(() => {
  const from = route.query.from
  if (typeof from !== 'string') return ''
  if (!from.startsWith('/words')) return ''
  return from
})

const validWordType = (type) => type === 'cet4' || type === 'cet6'
const validWordId = (id) => Number.isInteger(Number(id)) && Number(id) > 0

const normalizeSectionStatus = (rawStatus, hasContent, genStatus) => {
  const normalized = String(rawStatus || '').trim().toLowerCase()
  if (normalized === 'pending' || normalized === 'full' || normalized === 'fallback') return normalized
  if (hasContent) return 'full'
  const gen = String(genStatus || '').trim().toLowerCase()
  if (gen === 'pending' || gen === 'partial') return 'pending'
  return 'fallback'
}

const sectionStatusLabel = (status) => {
  if (status === 'full') return 'done'
  return status === 'pending' ? 'generating' : 'fallback'
}


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
  const type = String(detail.wordType || route.params.type || 'cet4').toLowerCase() === 'cet6' ? 'cet6' : 'cet4'
  router.push({
    path: '/words',
    query: {
      type,
      page: '1',
      size: '10'
    }
  })
}

const goAskAssistant = () => {
  router.push({
    path: '/assistant',
    query: {
      source: 'word_detail',
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

const handleSpeak = (accent) => {
  const result = speakWord(detail.english, accent)
  if (result.ok) return
  if (result.reason === 'unsupported') {
    ElMessage.warning('当前浏览器不支持语音播放')
  }
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
  const explainPending = !detail.llmContent?.smartExplain
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
      genStatus: 'pending',
      sentence: {},
      synonyms: [],
      mnemonic: {}
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
      explainStatus: 'pending',
      smartExplain: '',
      grammarUsage: ''
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
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 22px;
}

.state-card h3 {
  margin: 0 0 10px;
  color: var(--color-primary-strong);
}

.state-card p {
  margin: 0;
  color: var(--color-muted);
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
  color: var(--color-primary-strong);
  font-size: 44px;
  line-height: 1.1;
  font-weight: 700;
}

.back-link {
  padding: 0;
  margin-bottom: 8px;
  color: var(--color-muted);
}

.meta-inline {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.phonetic {
  color: var(--color-muted-soft);
  font-size: 18px;
}

.speak-btn {
  padding: 0 4px;
  color: var(--color-muted-strong);
}

.learn-btn {
  font-weight: 700;
}

.learn-btn:disabled {
  background: var(--color-border-soft);
  border-color: var(--color-border-soft);
  color: var(--color-surface);
}

.retry-btn {
  font-weight: 600;
}

.assistant-btn {
  font-weight: 600;
}

.meaning-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 18px 20px;
}

.meaning-card h3 {
  margin: 0 0 10px;
  color: var(--color-primary-strong);
  font-size: 16px;
}

.meaning-card p {
  margin: 0;
  color: var(--color-text);
  line-height: 1.8;
}


.item-status-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 12px 20px;
}

.item-status-card h3 {
  margin: 0 0 8px;
  color: var(--color-primary-strong);
  font-size: 14px;
}

.item-status-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--color-muted);
  font-size: 13px;
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

