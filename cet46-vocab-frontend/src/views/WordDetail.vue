<template>
  <section class="word-detail">
    <section v-if="invalidParam" class="state-card">
      <h3>参数无效</h3>
      <p>请从词库列表重新进入该单词详情页。</p>
    </section>

    <section v-else-if="loading" class="state-card">
      <el-skeleton animated :rows="6" />
    </section>

    <section v-else-if="emptyData" class="state-card">
      <h3>未找到该单词</h3>
      <p>该词条可能不存在或类型参数不匹配，请返回词库页重试。</p>
    </section>

    <template v-else>
      <div class="top-row">
        <div>
          <h1 class="word">{{ detail.english || '-' }}</h1>
          <div class="meta-inline">
            <span class="phonetic">{{ detail.phonetic || '' }}</span>
            <el-tag v-if="detail.pos" size="small" effect="plain">{{ detail.pos }}</el-tag>
          </div>
        </div>

        <el-button
          class="learn-btn"
          :disabled="isLearning || addLoading"
          :loading="addLoading"
          @click="handleAddLearn"
        >
          {{ isLearning ? '学习中' : '加入学习' }}
        </el-button>
      </div>

      <section class="meaning-card">
        <h3>基础释义</h3>
        <p>{{ detail.chinese || '-' }}</p>
      </section>

      <WordMetaPanel :llm-content="detail.llmContent" :gen-status="displayGenStatus" />
    </template>
  </section>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import WordMetaPanel from '@/components/word/WordMetaPanel.vue'

const route = useRoute()

const POLL_INTERVAL_MS = Number(import.meta.env.VITE_POLL_INTERVAL_MS || 3000)
const POLL_MAX_TIMES = Number(import.meta.env.VITE_POLL_MAX_TIMES || 30)
const FINAL_STATUS = ['full', 'partial', 'fallback']

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
    mnemonic: {}
  },
  progress: {
    isLearning: false
  }
})

const addLoading = ref(false)
const forceFallback = ref(false)
const pollTimer = ref(null)
const pollCount = ref(0)
const loading = ref(false)
const invalidParam = ref(false)
const emptyData = ref(false)

const isLearning = computed(() => !!detail.progress?.isLearning)
const displayGenStatus = computed(() => (forceFallback.value ? 'fallback' : (detail.llmContent?.genStatus || 'pending')))

const validWordType = (type) => type === 'cet4' || type === 'cet6'
const validWordId = (id) => Number.isInteger(Number(id)) && Number(id) > 0

const stopPolling = () => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

const applyData = (data) => {
  detail.wordId = data?.wordId ?? null
  detail.wordType = data?.wordType || ''
  detail.english = data?.english || ''
  detail.phonetic = data?.phonetic || ''
  detail.chinese = data?.chinese || ''
  detail.pos = data?.pos || ''
  detail.llmContent = data?.llmContent || { genStatus: 'pending', sentence: {}, synonyms: [], mnemonic: {} }
  detail.progress = data?.progress || { isLearning: false }
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
  return detail.llmContent?.genStatus || 'pending'
}

const startPollingIfNeeded = (status) => {
  stopPolling()
  if (status !== 'pending') return

  pollCount.value = 0
  pollTimer.value = setInterval(async () => {
    try {
      pollCount.value += 1
      const newStatus = await fetchDetail()
      if (FINAL_STATUS.includes(newStatus)) {
        stopPolling()
        forceFallback.value = false
        return
      }

      if (pollCount.value >= POLL_MAX_TIMES) {
        stopPolling()
        forceFallback.value = true
        ElMessage.warning('内容生成中，请稍后刷新')
      }
    } catch (error) {
      stopPolling()
    }
  }, POLL_INTERVAL_MS)
}

const initPage = async () => {
  stopPolling()
  forceFallback.value = false
  pollCount.value = 0
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
  } finally {
    loading.value = false
  }
}

const handleAddLearn = async () => {
  if (isLearning.value || invalidParam.value || emptyData.value) return
  addLoading.value = true
  try {
    await request.post('/word/learn/add', {
      wordId: Number(route.params.id),
      wordType: route.params.type
    })
    detail.progress = { ...(detail.progress || {}), isLearning: true }
    ElMessage.success('已加入学习计划')
  } finally {
    addLoading.value = false
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
  color: #1A2B4A;
}

.state-card p {
  margin: 0;
  color: #6b7a8d;
}

.top-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.word {
  margin: 0;
  color: #1A2B4A;
  font-size: 44px;
  line-height: 1.1;
  font-weight: 700;
}

.meta-inline {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.phonetic {
  color: #8896A8;
  font-size: 18px;
}

.learn-btn {
  background: #1A2B4A;
  border-color: #1A2B4A;
  color: #fff;
}

.learn-btn:disabled {
  background: #d0d7e3;
  border-color: #d0d7e3;
  color: #f8f9fb;
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
  color: #1A2B4A;
  font-size: 16px;
}

.meaning-card p {
  margin: 0;
  color: #2C3E50;
  line-height: 1.8;
}

@media (max-width: 768px) {
  .top-row {
    flex-direction: column;
  }

  .word {
    font-size: 36px;
  }
}
</style>
