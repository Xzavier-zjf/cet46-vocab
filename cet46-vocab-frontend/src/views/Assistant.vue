<template>
  <section class="assistant-page">
    <section class="intro-card">
      <div class="intro-head">
        <div>
          <h2>英语学习助手</h2>
          <p>我是你的四六级备考助手。可问单词、语法、例句、易混词和备考策略。</p>
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
          :disabled="loading"
          @click="sendQuickQuestion(q)"
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
          @keydown.ctrl.enter.prevent="send()"
        />
        <div class="actions">
          <div class="actions-left">
            <span class="mode-label">回答长度</span>
            <el-select v-model="answerMode" size="small" style="width: 132px">
              <el-option label="快速" value="quick" />
              <el-option label="平衡" value="balanced" />
              <el-option label="详细" value="detailed" />
            </el-select>
          </div>
          <div class="actions-right">
          <el-button :disabled="loading" @click="clearCurrentSession">清空当前对话</el-button>
          <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="send()">
            发送
          </el-button>
          </div>
        </div>
      </div>
    </section>

    <el-drawer v-model="historyVisible" title="历史对话管理" size="520px" append-to-body>
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

      <div class="history-filters second-row">
        <el-select v-model="historyGroupFilter" size="small" style="width: 220px">
          <el-option label="全部分组" value="all" />
          <el-option label="未分组" value="none" />
          <el-option
            v-for="group in groups"
            :key="group.id"
            :label="group.name"
            :value="group.id"
          />
        </el-select>
        <el-button size="small" @click="openCreateGroupDialog">新建分组</el-button>
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
            <span class="title-row">
              <span class="title">{{ session.title }}</span>
              <el-tag v-if="session.pinned" size="small" type="warning" effect="plain">置顶</el-tag>
              <el-tag v-if="groupNameById(session.groupId)" size="small" effect="plain">{{ groupNameById(session.groupId) }}</el-tag>
            </span>
            <span class="time">{{ formatTime(session.updatedAt) }}</span>
          </button>

          <el-dropdown trigger="click" :teleported="false" @command="(cmd) => handleMoreCommand(cmd, session)">
            <el-button class="more-btn" size="small" text title="更多" @click.stop>
              <span class="dots">⋯</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="session.pinned ? 'unpin' : 'pin'">
                  {{ session.pinned ? '取消置顶' : '置顶' }}
                </el-dropdown-item>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="copy">复制</el-dropdown-item>
                <div class="download-row">
                  <el-dropdown
                    trigger="hover"
                    placement="right-start"
                    :teleported="false"
                    @command="(cmd) => handleMoreCommand(cmd, session)"
                  >
                    <span class="download-label">下载</span>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="export_txt">导出 TXT</el-dropdown-item>
                        <el-dropdown-item command="export_json">导出 JSON</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
                <el-dropdown-item command="group">进入分组</el-dropdown-item>
                <el-dropdown-item divided command="delete">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="renameDialogVisible" title="重命名会话" width="420px">
      <el-input v-model="renameValue" maxlength="40" show-word-limit placeholder="请输入新的会话名称" />
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDialogVisible" title="进入分组" width="440px">
      <el-select v-model="groupTargetId" placeholder="选择分组（可留空为未分组）" clearable style="width: 100%">
        <el-option
          v-for="group in groups"
          :key="group.id"
          :label="group.name"
          :value="group.id"
        />
      </el-select>
      <div class="group-create-box">
        <el-input v-model="newGroupName" maxlength="20" placeholder="或输入新分组名称" />
      </div>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button @click="createGroupInDialog">新建分组</el-button>
        <el-button type="primary" @click="confirmGroupAssign">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assistantChat } from '@/api/assistant'
import request from '@/api/request'

const route = useRoute()

const STORAGE_KEY = 'assistant:sessions:v3'
const LEGACY_STORAGE_KEY = 'assistant:sessions:v2'
const loading = ref(false)
const learnActionLoading = ref(false)
const llmActionLoading = ref(false)
const question = ref('')
const answerMode = ref('balanced')
const historyVisible = ref(false)
const selectedIds = ref([])
const historyKeyword = ref('')
const historySort = ref('desc')
const historyGroupFilter = ref('all')

const renameDialogVisible = ref(false)
const renameValue = ref('')
const renameSessionId = ref('')

const groupDialogVisible = ref(false)
const groupSessionId = ref('')
const groupTargetId = ref('')
const newGroupName = ref('')

const state = loadState()
const sessions = ref(state.sessions)
const groups = ref(state.groups)
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
  const groupFilter = historyGroupFilter.value
  const list = sessions.value
    .filter((session) => !!session?.hasInteraction)
    .filter((session) => {
      if (groupFilter === 'all') return true
      if (groupFilter === 'none') return !session.groupId
      return session.groupId === groupFilter
    })
    .filter((session) => {
      if (!keyword) return true
      const title = String(session?.title || '').toLowerCase()
      const textContent = Array.isArray(session?.messages)
        ? session.messages
            .slice(-8)
            .map((m) => String(m?.content || ''))
            .join('\n')
            .toLowerCase()
        : ''
      return title.includes(keyword) || textContent.includes(keyword)
    })
    .slice()

  list.sort((a, b) => {
    const pinDiff = Number(!!b?.pinned) - Number(!!a?.pinned)
    if (pinDiff !== 0) return pinDiff
    return (Number(a?.updatedAt || 0) - Number(b?.updatedAt || 0)) * sortFactor
  })

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
  const sorted = sortForDefaultActivate(sessions.value.filter((s) => s.hasInteraction))
  if (sorted.length > 0) {
    activeSessionId.value = sorted[0].id
    return
  }
  createNewSession(true)
}

function sortForDefaultActivate(list) {
  return list.slice().sort((a, b) => {
    const pinDiff = Number(!!b?.pinned) - Number(!!a?.pinned)
    if (pinDiff !== 0) return pinDiff
    return Number(b?.updatedAt || 0) - Number(a?.updatedAt || 0)
  })
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
    pinned: false,
    groupId: null,
    context,
    messages: [{ id: now + 1, role: 'assistant', content: greeting }]
  }
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  persistState()
}

function openHistory() {
  selectedIds.value = []
  historyKeyword.value = ''
  historySort.value = 'desc'
  historyGroupFilter.value = 'all'
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
    const fallback = sortForDefaultActivate(sessions.value.filter((s) => s.hasInteraction))[0]
    if (fallback) {
      activeSessionId.value = fallback.id
    } else {
      createNewSession(true)
    }
  }
  persistState()
}

function deleteSelected() {
  if (selectedIds.value.length === 0) return
  const set = new Set(selectedIds.value)
  const removingActive = set.has(activeSessionId.value)
  sessions.value = sessions.value.filter((s) => !set.has(s.id))
  selectedIds.value = []
  if (removingActive) {
    const fallback = sortForDefaultActivate(sessions.value.filter((s) => s.hasInteraction))[0]
    if (fallback) {
      activeSessionId.value = fallback.id
    } else {
      createNewSession(true)
    }
  }
  persistState()
}

function clearCurrentSession() {
  const idx = sessions.value.findIndex((s) => s.id === activeSessionId.value)
  if (idx < 0) return
  const now = Date.now()
  const wasInteracted = !!sessions.value[idx].hasInteraction
  const contextWord = sessions.value[idx].context?.word
  sessions.value[idx].messages = [
    {
      id: now,
      role: 'assistant',
      content: contextWord ? `已清空对话。继续围绕 ${contextWord} 提问即可。` : '已清空当前对话。你可以继续提问。'
    }
  ]
  sessions.value[idx].updatedAt = now
  sessions.value[idx].hasInteraction = wasInteracted
  sessions.value[idx].pinned = false
  sessions.value[idx].title = contextWord ? `单词：${contextWord}` : `会话 ${new Date(now).toLocaleString()}`
  persistState()
}

function sendQuickQuestion(content) {
  send(content)
}

async function send(contentOverride = '') {
  const content = String(contentOverride || question.value || '').trim()
  if (!content || loading.value) return

  pushMessage({ id: Date.now(), role: 'user', content })
  question.value = ''
  loading.value = true

  try {
    const res = await assistantChat({
      question: content,
      answerMode: answerMode.value,
      wordContext: activeContext.value?.word ? activeContext.value : null,
      history: buildHistory()
    })
    const answer = normalizeAnswer(res?.data?.answer)
    pushMessage({
      id: Date.now() + 1,
      role: 'assistant',
      content: answer || '我暂时没有整理出有效回答，你可以换个问法再试一次。'
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
  persistState()
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
  const value = String(raw || '').trim()
  if (!value) return ''
  try {
    const obj = JSON.parse(value)
    if (obj && typeof obj === 'object' && typeof obj.answer === 'string') {
      return obj.answer.trim()
    }
  } catch {
    // Ignore.
  }
  if (value.startsWith('{') && value.includes('"answer"')) {
    const m = value.match(/"answer"\s*:\s*"([\s\S]*?)"/)
    if (m && m[1]) {
      return m[1].replace(/\\n/g, '\n').replace(/\\"/g, '"').trim()
    }
  }
  return value
}

function handleMoreCommand(command, session) {
  if (!session) return
  if (command === 'pin') {
    session.pinned = true
    session.updatedAt = Date.now()
    persistState()
    return
  }
  if (command === 'unpin') {
    session.pinned = false
    persistState()
    return
  }
  if (command === 'rename') {
    renameSessionId.value = session.id
    renameValue.value = session.title || ''
    renameDialogVisible.value = true
    return
  }
  if (command === 'copy') {
    copySession(session)
    return
  }
  if (command === 'export_json') {
    exportSessionAsJson(session)
    return
  }
  if (command === 'export_txt') {
    exportSessionAsTxt(session)
    return
  }
  if (command === 'group') {
    groupSessionId.value = session.id
    groupTargetId.value = session.groupId || ''
    newGroupName.value = ''
    groupDialogVisible.value = true
    return
  }
  if (command === 'delete') {
    deleteSession(session.id)
  }
}

function confirmRename() {
  const title = renameValue.value.trim()
  if (!title) {
    ElMessage.warning('请输入会话名称')
    return
  }
  const session = sessions.value.find((s) => s.id === renameSessionId.value)
  if (!session) {
    renameDialogVisible.value = false
    return
  }
  session.title = title
  session.updatedAt = Date.now()
  renameDialogVisible.value = false
  persistState()
}

function copySession(session) {
  const now = Date.now()
  const copied = {
    ...session,
    id: String(now),
    title: `${session.title || '会话'} - 副本`,
    updatedAt: now,
    hasInteraction: true,
    messages: (session.messages || []).map((m, i) => ({
      ...m,
      id: now + i + 1
    }))
  }
  sessions.value.unshift(copied)
  activeSessionId.value = copied.id
  persistState()
  ElMessage.success('已复制会话')
}

function exportSessionAsJson(session) {
  const payload = {
    title: session.title,
    createdAt: formatDateTime(session.updatedAt),
    group: groupNameById(session.groupId) || '',
    messages: session.messages || []
  }
  downloadFile(`${safeFileName(session.title)}.json`, JSON.stringify(payload, null, 2), 'application/json;charset=utf-8')
}

function exportSessionAsTxt(session) {
  const lines = []
  lines.push(`会话标题: ${session.title || ''}`)
  lines.push(`更新时间: ${formatDateTime(session.updatedAt)}`)
  lines.push(`分组: ${groupNameById(session.groupId) || '未分组'}`)
  lines.push('')
  ;(session.messages || []).forEach((m) => {
    const role = m.role === 'assistant' ? 'AI' : '用户'
    lines.push(`${role}: ${m.content || ''}`)
    lines.push('')
  })
  downloadFile(`${safeFileName(session.title)}.txt`, lines.join('\n'), 'text/plain;charset=utf-8')
}

function downloadFile(fileName, content, mimeType) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function openCreateGroupDialog() {
  ElMessageBox.prompt('请输入分组名称', '新建分组', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '分组名称不能为空'
  }).then(({ value }) => {
    const name = String(value || '').trim()
    if (!name) return
    const existing = groups.value.find((g) => g.name.toLowerCase() === name.toLowerCase())
    if (existing) {
      ElMessage.warning('分组已存在')
      return
    }
    groups.value.unshift({
      id: `g_${Date.now()}`,
      name,
      createdAt: Date.now()
    })
    persistState()
    ElMessage.success('分组已创建')
  }).catch(() => {})
}

function createGroupInDialog() {
  const name = newGroupName.value.trim()
  if (!name) {
    ElMessage.warning('请输入分组名称')
    return
  }
  const existing = groups.value.find((g) => g.name.toLowerCase() === name.toLowerCase())
  if (existing) {
    groupTargetId.value = existing.id
    ElMessage.success('已选择现有分组')
    return
  }
  const group = {
    id: `g_${Date.now()}`,
    name,
    createdAt: Date.now()
  }
  groups.value.unshift(group)
  groupTargetId.value = group.id
  newGroupName.value = ''
  persistState()
  ElMessage.success('已创建分组')
}

function confirmGroupAssign() {
  const session = sessions.value.find((s) => s.id === groupSessionId.value)
  if (!session) {
    groupDialogVisible.value = false
    persistState()
    return
  }
  session.groupId = groupTargetId.value || null
  session.updatedAt = Date.now()
  groupDialogVisible.value = false
  persistState()
  ElMessage.success('分组已更新')
}

function groupNameById(groupId) {
  if (!groupId) return ''
  const group = groups.value.find((g) => g.id === groupId)
  return group?.name || ''
}

function loadState() {
  const parsed = parseStorage(STORAGE_KEY)
  if (parsed) {
    return normalizeState(parsed)
  }

  const legacy = parseStorage(LEGACY_STORAGE_KEY)
  if (legacy) {
    const normalized = normalizeState({ sessions: Array.isArray(legacy) ? legacy : legacy.sessions, groups: [] })
    return normalized
  }

  return { sessions: [], groups: [] }
}

function parseStorage(key) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function normalizeState(raw) {
  const sessionList = Array.isArray(raw) ? raw : Array.isArray(raw?.sessions) ? raw.sessions : []
  const groupList = Array.isArray(raw?.groups) ? raw.groups : []

  const sessionsNormalized = sessionList
    .filter((s) => s && s.id && Array.isArray(s.messages))
    .map((s) => ({
      ...s,
      pinned: !!s.pinned,
      groupId: s.groupId || null,
      hasInteraction: typeof s.hasInteraction === 'boolean'
        ? s.hasInteraction
        : s.messages.some((m) => m?.role === 'user')
    }))
    .filter((s) => s.hasInteraction)
    .slice(0, 100)

  const groupsNormalized = groupList
    .filter((g) => g && g.id && g.name)
    .slice(0, 100)

  return { sessions: sessionsNormalized, groups: groupsNormalized }
}

function persistState() {
  const stableSessions = sessions.value
    .filter((s) => s && s.hasInteraction)
    .slice(0, 100)

  const activeExists = stableSessions.some((s) => s.id === activeSessionId.value)
  if (!activeExists) {
    const tmp = sessions.value.find((s) => s.id === activeSessionId.value)
    if (!tmp) {
      const fallback = sortForDefaultActivate(stableSessions)[0]
      if (fallback) activeSessionId.value = fallback.id
    }
  }

  const payload = {
    sessions: stableSessions,
    groups: groups.value.slice(0, 100)
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}

function formatTime(ts) {
  const d = new Date(Number(ts || 0))
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDateTime(ts) {
  const d = new Date(Number(ts || 0))
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
}

function safeFileName(name) {
  const base = String(name || 'chat').trim() || 'chat'
  return base.replace(/[\\/:*?"<>|]/g, '_').slice(0, 64)
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
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions-right {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.mode-label {
  font-size: 12px;
  color: #61748d;
}

.history-filters {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  margin-bottom: 10px;
}

.history-filters.second-row {
  grid-template-columns: auto auto;
  justify-content: space-between;
}

.history-tools {
  display: flex;
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

.title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.history-main .title {
  color: #1f2f45;
  font-size: 13px;
}

.history-main .time {
  display: block;
  color: #75879f;
  font-size: 12px;
  margin-top: 4px;
}

.more-btn {
  opacity: 0.5;
}

.history-item:hover .more-btn {
  opacity: 1;
}

.dots {
  font-size: 18px;
  line-height: 1;
}

.download-row {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  color: var(--el-text-color-regular);
}

.download-row:hover {
  background: var(--el-dropdown-menuItem-hover-fill);
  color: var(--el-dropdown-menuItem-hover-color);
}

.download-label {
  display: block;
  width: 100%;
  cursor: pointer;
}

.group-create-box {
  margin-top: 10px;
}

@media (max-width: 768px) {
  .intro-head {
    flex-direction: column;
  }

  .history-filters.second-row {
    grid-template-columns: 1fr;
  }

  .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .actions-right {
    justify-content: flex-end;
  }
}
</style>
