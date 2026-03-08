<template>
  <section class="assistant-page">
    <section class="intro-card">
      <div class="intro-head">
        <div>
          <h2>英语学习助手</h2>
          <p>我是你的四六级备考助手。可问单词记忆、语法、例句、易混词和备考策略。</p>
        </div>
        <div class="intro-actions">
          <el-button plain @click="openHistory">历史对话</el-button>
          <el-button type="primary" plain @click="createNewSession(true)">新建对话</el-button>
        </div>
      </div>
      <div v-if="activeContext.word" class="context">
        <el-tag size="small" type="info">{{ activeContext.word }}</el-tag>
        <el-tag v-if="activeContext.pos" size="small" effect="plain">{{ activeContext.pos }}</el-tag>
        <span class="context-text">{{ activeContext.chinese || '' }}</span>
      </div>
    </section>

    <section class="chat-card">
      <div class="messages">
        <div v-for="msg in messages" :key="msg.id" class="msg" :class="msg.role">
          <div class="msg-body">
            <div class="bubble">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && hasWordAction" class="msg-actions">
              <el-button
                size="small"
                text
                :loading="learnActionLoading"
                :disabled="learnActionLoading || llmActionLoading"
                @click="handleAddWordToLearn"
              >
                加入该词学习
              </el-button>
              <el-button
                size="small"
                text
                :loading="llmActionLoading"
                :disabled="learnActionLoading || llmActionLoading"
                @click="handleRetryWordLlm"
              >
                重试该词AI生成
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="loading" class="msg assistant">
          <div class="bubble">正在思考中...</div>
        </div>
      </div>

      <div class="quick-ask">
        <el-button
          v-for="q in quickQuestions"
          :key="q"
          size="small"
          plain
          @click="fillQuestion(q)"
        >
          {{ q }}
        </el-button>
      </div>

      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="输入你的问题，例如：abandon 怎么记更快？"
          @keydown.ctrl.enter.prevent="send"
        />
        <div class="actions">
          <el-button :disabled="loading" @click="clearCurrentSession">清空当前对话</el-button>
          <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </section>

    <el-drawer v-model="historyVisible" title="历史对话管理" size="420px" append-to-body>
      <div class="history-filters">
        <el-input
          v-model="historyKeyword"
          size="small"
          clearable
          placeholder="搜索会话标题或内容"
        />
        <el-select v-model="historySort" size="small" style="width: 128px">
          <el-option label="最新优先" value="desc" />
          <el-option label="最早优先" value="asc" />
        </el-select>
      </div>
      <div class="history-tools">
        <el-button size="small" @click="toggleSelectAll">
          {{ allSelected ? '取消全选' : '全选' }}
        </el-button>
        <el-button size="small" type="danger" plain :disabled="selectedIds.length === 0" @click="deleteSelected">
          批量删除
        </el-button>
      </div>
      <div class="history-list">
        <div v-for="session in filteredSortedSessions" :key="session.id" class="history-item">
          <el-checkbox
            :model-value="selectedIds.includes(session.id)"
            @change="toggleSelect(session.id)"
          />
          <button class="history-main" @click="enterSession(session.id)">
            <span class="title">{{ session.title }}</span>
            <span class="time">{{ formatTime(session.updatedAt) }}</span>
          </button>
          <el-button size="small" text type="danger" @click="deleteSession(session.id)">删除</el-button>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assistantChat } from '@/api/assistant'
import request from '@/api/request'

const route = useRoute()

const STORAGE_KEY = 'assistant:sessions:v2'
const loading = ref(false)
const learnActionLoading = ref(false)
const llmActionLoading = ref(false)
const question = ref('')
const historyVisible = ref(false)
const selectedIds = ref([])
const historyKeyword = ref('')
const historySort = ref('desc')

const sessions = ref(loadSessions())
const activeSessionId = ref('')

const routeWordContext = computed(() => ({
  wordId: toNumber(route.query.wordId),
  wordType: text(route.query.wordType),
  word: text(route.query.word),
  phonetic: text(route.query.phonetic),
  pos: text(route.query.pos),
  chinese: text(route.query.chinese),
  fromPage: text(route.query.from) || '/words'
}))

const activeSession = computed(() => sessions.value.find((s) => s.id === activeSessionId.value) || null)
const activeContext = computed(() => activeSession.value?.context || {})
const hasWordAction = computed(() => !!activeContext.value.wordId && !!activeContext.value.wordType)
const messages = computed(() => activeSession.value?.messages || [])
const filteredSortedSessions = computed(() => {
  const keyword = historyKeyword.value.trim().toLowerCase()
  const sortFactor = historySort.value === 'asc' ? 1 : -1
  const list = sessions.value
    .filter((session) => !!session?.hasInteraction)
    .filter((session) => {
      if (!keyword) return true
      const title = String(session?.title || '').toLowerCase()
      const text = Array.isArray(session?.messages)
        ? session.messages
            .slice(-6)
            .map((m) => String(m?.content || ''))
            .join('\n')
            .toLowerCase()
        : ''
      return title.includes(keyword) || text.includes(keyword)
    })
    .slice()
  list.sort((a, b) => (Number(a?.updatedAt || 0) - Number(b?.updatedAt || 0)) * sortFactor)
  return list
})
const visibleSessionIds = computed(() => filteredSortedSessions.value.map((s) => s.id))
const allSelected = computed(() => {
  const ids = visibleSessionIds.value
  if (ids.length === 0) return false
  return ids.every((id) => selectedIds.value.includes(id))
})

const quickQuestions = computed(() => {
  if (activeContext.value.word) {
    return [
      `${activeContext.value.word}怎么记更快？`,
      `${activeContext.value.word}常见搭配有哪些？`,
      `给我2个 ${activeContext.value.word} 的四六级例句`
    ]
  }
  return [
    '帮我制定本周四六级背词计划',
    '我总记不住单词，怎么复习更高效？',
    '四六级阅读和写作如何分配时间？'
  ]
})

initSession()

function initSession() {
  if (sessions.value.length === 0) {
    const hasRouteWord = !!routeWordContext.value.word
    createNewSession(!hasRouteWord)
    return
  }
  activeSessionId.value = sessions.value[0].id
}

function createNewSession(forceGlobal) {
  const now = Date.now()
  sessions.value = sessions.value.filter((s) => s && s.hasInteraction)
  const context = forceGlobal ? {} : { ...routeWordContext.value }
  const title = context.word ? `单词：${context.word}` : `会话 ${new Date(now).toLocaleString()}`
  const greeting = context.word
    ? `你好，围绕单词 ${context.word}，你可以问我“怎么记”“如何造句”“和哪个词易混”。`
    : '你好，我是你的四六级学习助手。'
  const session = {
    id: String(now),
    title,
    updatedAt: now,
    hasInteraction: false,
    context,
    messages: [{ id: now + 1, role: 'assistant', content: greeting }]
  }
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  persistSessions()
}

function openHistory() {
  selectedIds.value = []
  historyKeyword.value = ''
  historySort.value = 'desc'
  historyVisible.value = true
}

function enterSession(sessionId) {
  activeSessionId.value = sessionId
  historyVisible.value = false
}

function toggleSelect(sessionId) {
  const idx = selectedIds.value.indexOf(sessionId)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(sessionId)
  }
}

function toggleSelectAll() {
  const ids = visibleSessionIds.value
  if (ids.length === 0) return
  if (allSelected.value) {
    selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
  } else {
    selectedIds.value = Array.from(new Set([...selectedIds.value, ...ids]))
  }
}

function deleteSession(sessionId) {
  sessions.value = sessions.value.filter((s) => s.id !== sessionId)
  selectedIds.value = selectedIds.value.filter((id) => id !== sessionId)
  if (activeSessionId.value === sessionId) {
    if (sessions.value.length > 0) {
      activeSessionId.value = sessions.value[0].id
    } else {
      createNewSession(true)
    }
  }
  persistSessions()
}

function deleteSelected() {
  if (selectedIds.value.length === 0) return
  const set = new Set(selectedIds.value)
  const removingActive = set.has(activeSessionId.value)
  sessions.value = sessions.value.filter((s) => !set.has(s.id))
  selectedIds.value = []
  if (sessions.value.length === 0) {
    createNewSession(true)
  } else if (removingActive) {
    activeSessionId.value = sessions.value[0].id
  }
  persistSessions()
}

function fillQuestion(q) {
  question.value = q
}

function clearCurrentSession() {
  const idx = sessions.value.findIndex((s) => s.id === activeSessionId.value)
  if (idx < 0) return
  const now = Date.now()
  const contextWord = sessions.value[idx].context?.word
  sessions.value[idx].messages = [
    {
      id: now,
      role: 'assistant',
      content: contextWord ? `已清空对话。继续围绕 ${contextWord} 提问即可。` : '已清空当前对话。你可以继续提问。'
    }
  ]
  sessions.value[idx].updatedAt = now
  persistSessions()
}

async function send() {
  const content = question.value.trim()
  if (!content || loading.value) return
  pushMessage({ id: Date.now(), role: 'user', content })
  question.value = ''
  loading.value = true

  try {
    const res = await assistantChat({
      question: content,
      wordContext: activeContext.value?.word ? activeContext.value : null,
      history: buildHistory()
    })
    const answer = normalizeAnswer(res?.data?.answer)
    pushMessage({
      id: Date.now() + 1,
      role: 'assistant',
      content: answer || '我暂时没整理出有效回答，你可以换个问法再试一次。'
    })
  } catch (error) {
    const rawMessage = String(error?.message || '')
    if (error?.code === 'ECONNABORTED' || rawMessage.includes('timeout')) {
      ElMessage.warning('学习助手响应较慢，请重试一次或缩短问题后再试')
    } else {
      ElMessage.warning(error?.businessMessage || error?.message || '学习助手暂时不可用')
    }
  } finally {
    loading.value = false
  }
}

function pushMessage(msg) {
  const idx = sessions.value.findIndex((s) => s.id === activeSessionId.value)
  if (idx < 0) return
  sessions.value[idx].messages.push(msg)
  sessions.value[idx].updatedAt = Date.now()
  if (msg.role === 'user') {
    sessions.value[idx].hasInteraction = true
  }
  if (!sessions.value[idx].title?.startsWith('单词：') && msg.role === 'user') {
    sessions.value[idx].title = msg.content.slice(0, 16)
  }
  persistSessions()
}

function buildHistory() {
  return messages.value
    .slice(-6)
    .map((m) => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content }))
}

async function handleAddWordToLearn() {
  if (!hasWordAction.value || learnActionLoading.value) return
  learnActionLoading.value = true
  try {
    await request.post('/word/learn/add', {
      wordId: activeContext.value.wordId,
      wordType: activeContext.value.wordType
    })
    ElMessage.success('已将该词加入学习计划')
  } catch (error) {
    ElMessage.warning(error?.businessMessage || error?.message || '加入学习失败，请稍后重试')
  } finally {
    learnActionLoading.value = false
  }
}

async function handleRetryWordLlm() {
  if (!hasWordAction.value || llmActionLoading.value) return
  llmActionLoading.value = true
  try {
    await request.post('/word/llm/generate', {
      wordId: activeContext.value.wordId,
      wordType: activeContext.value.wordType
    })
    try {
      await request.post('/word/llm/generate-explain', {
        wordId: activeContext.value.wordId,
        wordType: activeContext.value.wordType
      })
    } catch {
      // Ignore explain errors.
    }
    ElMessage.success('已触发该词AI重试任务')
  } catch (error) {
    ElMessage.warning(error?.businessMessage || error?.message || '触发AI重试失败，请稍后重试')
  } finally {
    llmActionLoading.value = false
  }
}

function normalizeAnswer(raw) {
  const text = String(raw || '').trim()
  if (!text) return ''
  try {
    const obj = JSON.parse(text)
    if (obj && typeof obj === 'object' && typeof obj.answer === 'string') {
      return obj.answer.trim()
    }
  } catch {
    // Ignore.
  }
  if (text.startsWith('{') && text.includes('"answer"')) {
    const m = text.match(/"answer"\s*:\s*"([\s\S]*?)"/)
    if (m && m[1]) {
      return m[1].replace(/\\n/g, '\n').replace(/\\"/g, '"').trim()
    }
  }
  return text
}

function loadSessions() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((s) => s && s.id && Array.isArray(s.messages))
      .map((s) => ({
        ...s,
        hasInteraction: typeof s.hasInteraction === 'boolean'
          ? s.hasInteraction
          : s.messages.some((m) => m?.role === 'user')
      }))
      .filter((s) => s.hasInteraction)
      .slice(0, 50)
  } catch {
    return []
  }
}

function persistSessions() {
  const stableSessions = sessions.value
    .filter((s) => s && s.hasInteraction)
    .slice(0, 50)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stableSessions))
}

function formatTime(ts) {
  const d = new Date(Number(ts || 0))
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function text(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function toNumber(value) {
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : null
}
</script>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro-card,
.chat-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.intro-card {
  padding: 16px 18px;
}

.intro-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.intro-actions {
  display: flex;
  gap: 8px;
}

.intro-card h2 {
  margin: 0 0 8px;
  color: #1a2b4a;
}

.intro-card p {
  margin: 0;
  color: #4b5d73;
}

.context {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.context-text {
  color: #627389;
  font-size: 13px;
}

.chat-card {
  padding: 14px;
}

.messages {
  min-height: 320px;
  max-height: 58vh;
  overflow: auto;
  padding-right: 6px;
}

.msg {
  display: flex;
  margin-bottom: 12px;
}

.msg.user {
  justify-content: flex-end;
}

.msg-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bubble {
  max-width: 82%;
  white-space: pre-wrap;
  line-height: 1.65;
  padding: 10px 12px;
  border-radius: 10px;
}

.msg.user .bubble {
  background: #1a2b4a;
  color: #fff;
}

.msg.assistant .bubble {
  background: #f3f6fa;
  color: #23384f;
}

.msg-actions {
  display: flex;
  gap: 8px;
  padding-left: 4px;
}

.quick-ask {
  margin: 8px 0 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.composer {
  border-top: 1px solid #edf1f6;
  padding-top: 12px;
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.history-tools {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.history-filters {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  margin-bottom: 10px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: center;
  border: 1px solid #e7edf5;
  background: #f8fbff;
  border-radius: 8px;
  padding: 8px;
}

.history-main {
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.history-main .title {
  display: block;
  color: #1f2f45;
  font-size: 13px;
}

.history-main .time {
  color: #75879f;
  font-size: 12px;
}

@media (max-width: 768px) {
  .intro-head {
    flex-direction: column;
  }
}
</style>
