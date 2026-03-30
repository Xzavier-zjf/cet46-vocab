<template>
  <section class="assistant-page">
    <section class="intro-card">
      <div class="intro-head">
        <div>
          <h2>英语学习助手</h2>
          <p>我是你的四六级备考助手。可问单词、语法、例句、易混词和备考策略。</p>
        </div>
        <div class="intro-actions">
          <BtnSecondary :disabled="loading" @click="clearCurrentSession">重置当前会话</BtnSecondary>
          <BtnSecondary @click="openHistory">历史对话</BtnSecondary>
          <BtnPrimary @click="createNewSession(true)">新建对话</BtnPrimary>
        </div>
      </div>
      <div v-if="activeContext.word" class="context">
        <el-tag size="small" type="info">{{ activeContext.word }}</el-tag>
        <el-tag v-if="activeContext.pos" size="small" effect="plain">{{ activeContext.pos }}</el-tag>
        <span class="context-text">{{ activeContext.chinese || '' }}</span>
      </div>
      <div v-if="activeContext.fromPage" class="source-row">
        <span>来自单词详情：{{ activeContext.word || '当前单词' }}</span>
        <el-button text size="small" @click="goBackToSource">返回该单词</el-button>
      </div>
    </section>

    <section class="chat-card">
      <div class="messages">
        <div v-for="msg in messages" :key="msg.id" class="msg" :class="msg.role">
          <div class="msg-body">
            <div class="bubble">{{ msg.content }}</div>
            <div v-if="msg.role === 'assistant' && hasAutoContinuation(msg)" class="msg-meta">
              <el-tag size="small" type="warning" effect="plain">已自动续写</el-tag>
            </div>
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
            <div class="msg-tools">
              <template v-if="msg.role === 'user'">
                <el-button class="tool-btn" text size="small" @click="copyMessageContent(msg.content)">⧉ 复制</el-button>
                <el-button class="tool-btn" text size="small" @click="editUserMessage(msg.id)">✎ 编辑</el-button>
                <el-button
                  v-if="isLatestUserMessage(msg)"
                  class="tool-btn danger"
                  text
                  size="small"
                  @click="deleteUserMessage(msg.id)"
                >
                  ⌫ 删除
                </el-button>
              </template>
              <template v-else>
                <el-button class="tool-btn" text size="small" @click="copyMessageContent(msg.content)">⧉ 复制</el-button>
                <el-button
                  class="tool-btn"
                  :class="{ active: msg.feedback === 'up' }"
                  text
                  size="small"
                  @click="setAssistantFeedback(msg.id, 'up')"
                >
                  ↑ 点赞
                </el-button>
                <el-button
                  class="tool-btn"
                  :class="{ active: msg.feedback === 'down' }"
                  text
                  size="small"
                  @click="setAssistantFeedback(msg.id, 'down')"
                >
                  ↓ 点踩
                </el-button>
                <el-button
                  class="tool-btn"
                  text
                  size="small"
                  :disabled="loading || regeneratingMessageId === String(msg.id)"
                  :loading="regeneratingMessageId === String(msg.id)"
                  @click="regenerateAssistantMessage(msg.id)"
                >
                  ↻ 重新生成
                </el-button>
              </template>
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
          @keydown="handleComposerKeydown"
        />
        <div class="actions">
          <div class="actions-right">
            <el-button
              v-if="loading"
              type="warning"
              plain
              @click="stopCurrentReply"
            >
              暂停
            </el-button>
            <BtnPrimary
              v-else
              :disabled="!question.trim()"
              @click="send()"
            >
              发送
            </BtnPrimary>
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
        <div class="group-actions">
          <el-dropdown trigger="click" :teleported="false" @command="handleGroupManageCommand">
            <el-button size="small">分组管理</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="create">新建分组</el-dropdown-item>
                <el-dropdown-item command="edit" :disabled="groups.length === 0">编辑分组</el-dropdown-item>
                <el-dropdown-item command="delete" :disabled="groups.length === 0">删除分组</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="history-tools">
        <BtnSecondary size="small" @click="toggleSelectAll">
          {{ allSelected ? '取消全选' : '全选' }}
        </BtnSecondary>
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
                <el-dropdown-item command="remove_group">删除分组</el-dropdown-item>
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
        <BtnSecondary @click="renameDialogVisible = false">取消</BtnSecondary>
        <BtnPrimary @click="confirmRename">确定</BtnPrimary>
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
        <BtnSecondary @click="groupDialogVisible = false">取消</BtnSecondary>
        <BtnSecondary @click="createGroupInDialog">新建分组</BtnSecondary>
        <BtnPrimary @click="confirmGroupAssign">确定</BtnPrimary>
      </template>
    </el-dialog>

    <el-dialog v-model="groupDeleteDialogVisible" title="删除分组" width="420px">
      <el-select
        v-model="groupDeleteTargetId"
        placeholder="请选择要删除的分组"
        style="width: 100%"
        clearable
      >
        <el-option
          v-for="group in groups"
          :key="group.id"
          :label="group.name"
          :value="group.id"
        />
      </el-select>
      <template #footer>
        <BtnSecondary @click="groupDeleteDialogVisible = false">取消</BtnSecondary>
        <el-button type="danger" @click="confirmDeleteGroup">删除分组</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupEditDialogVisible" title="编辑分组" width="560px">
      <div class="group-edit-block">
        <el-select
          v-model="groupEditTargetId"
          placeholder="请选择要编辑的分组"
          style="width: 100%"
          clearable
          @change="onGroupEditTargetChange"
        >
          <el-option
            v-for="group in groups"
            :key="group.id"
            :label="group.name"
            :value="group.id"
          />
        </el-select>
      </div>
      <div v-if="groupEditTargetId" class="group-edit-block">
        <div class="group-edit-title">重命名分组</div>
        <div class="group-edit-rename-row">
          <el-input v-model="groupEditName" maxlength="20" placeholder="请输入新的分组名称" />
          <BtnPrimary @click="confirmRenameGroup">保存名称</BtnPrimary>
        </div>
      </div>
      <div v-if="groupEditTargetId" class="group-edit-block">
        <div class="group-edit-title">会话分组管理</div>
        <div class="group-edit-batch-row">
          <el-select
            v-model="groupBulkTargetId"
            size="small"
            style="width: 200px"
            placeholder="批量转移到"
            clearable
          >
            <el-option label="未分组" value="" />
            <el-option
              v-for="group in groups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
              :disabled="group.id === groupEditTargetId"
            />
          </el-select>
          <el-button size="small" type="primary" plain @click="bulkReassignGroupSessions">批量转移该分组全部会话</el-button>
        </div>
        <div v-if="groupEditSessions.length === 0" class="group-edit-empty">该分组下暂无会话</div>
        <div v-else class="group-edit-list">
          <div v-for="session in groupEditSessions" :key="session.id" class="group-edit-item">
            <span class="group-edit-session-title">{{ session.title || '未命名会话' }}</span>
            <el-select
              size="small"
              :model-value="session.groupId || ''"
              style="width: 170px"
              @change="(value) => reassignSessionGroup(session.id, value)"
            >
              <el-option label="未分组" value="" />
              <el-option
                v-for="group in groups"
                :key="group.id"
                :label="group.name"
                :value="group.id"
              />
            </el-select>
          </div>
        </div>
      </div>
      <template #footer>
        <BtnSecondary @click="groupEditDialogVisible = false">关闭</BtnSecondary>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assistantChat } from '@/api/assistant'
import request from '@/api/request'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

const route = useRoute()
const router = useRouter()

const STORAGE_KEY = 'assistant:sessions:v3'
const LEGACY_STORAGE_KEY = 'assistant:sessions:v2'
const ACTIVE_SESSION_KEY = 'assistant:active-session:v1'
const loading = ref(false)
const regeneratingMessageId = ref('')
const learnActionLoading = ref(false)
const llmActionLoading = ref(false)
const EMPTY_RESPONSE_HINT = '当前内容为空，请重新生成。'
const ABORT_REASON_MANUAL_STOP = 'manual-stop'
const ABORT_REASON_DELETE_QUESTION = 'delete-question'
let sendController = null
let abortReason = ''
let pendingSessionId = ''
let pendingUserMessageId = null
const question = ref('')
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
const groupDeleteDialogVisible = ref(false)
const groupDeleteTargetId = ref('')
const groupEditDialogVisible = ref(false)
const groupEditTargetId = ref('')
const groupEditName = ref('')
const groupBulkTargetId = ref('')

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
const latestUserMessageId = computed(() => {
  const list = messages.value
  for (let i = list.length - 1; i >= 0; i -= 1) {
    if (list[i]?.role === 'user') return list[i].id
  }
  return null
})
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
const groupEditSessions = computed(() => {
  const targetId = groupEditTargetId.value
  if (!targetId) return []
  return sessions.value.filter((s) => s?.groupId === targetId)
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
  const hasRouteWord = !!routeWordContext.value.word && !!routeWordContext.value.wordId
  if (hasRouteWord) {
    createNewSession(false)
    return
  }
  if (sessions.value.length === 0) {
    createNewSession(true)
    return
  }
  const storedActiveId = loadActiveSessionId()
  if (storedActiveId && sessions.value.some((s) => s.id === storedActiveId)) {
    activeSessionId.value = storedActiveId
    return
  }
  const sorted = sortForDefaultActivate(sessions.value)
  if (sorted.length > 0) {
    activeSessionId.value = sorted[0].id
    persistState()
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
  sessions.value = sessions.value.filter((s) => s && (s.hasInteraction || s.id === activeSessionId.value))
  const context = forceGlobal ? {} : { ...routeWordContext.value }
  const title = context.word ? `单词：${context.word}` : `会话 ${new Date(now).toLocaleString()}`
  const greeting = '你好，我是你的四六级学习助手。'
  const session = {
    id: buildSessionId(),
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
  persistState()
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
      content: contextWord ? `已重置会话。继续围绕 ${contextWord} 提问即可。` : '已重置当前会话。你可以继续提问。'
    }
  ]
  sessions.value[idx].updatedAt = now
  sessions.value[idx].hasInteraction = wasInteracted
  sessions.value[idx].pinned = false
  sessions.value[idx].title = contextWord ? `单词：${contextWord}` : `会话 ${new Date(now).toLocaleString()}`
  persistState()
}

function goBackToSource() {
  const fromPage = activeContext.value?.fromPage
  if (fromPage && typeof fromPage === 'string') {
    router.push(fromPage)
    return
  }
  router.push('/words')
}

function sendQuickQuestion(content) {
  send(content)
}

function stopCurrentReply() {
  if (!loading.value || !sendController) return
  abortReason = ABORT_REASON_MANUAL_STOP
  sendController.abort()
}

function handleComposerKeydown(event) {
  if (event.key !== 'Enter') return
  if (event.shiftKey) return
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  send()
}

async function send(contentOverride = '', targetSessionId = '') {
  const content = String(contentOverride || question.value || '').trim()
  if (!content || loading.value) return
  if (targetSessionId) {
    activeSessionId.value = targetSessionId
  }
  const requestSessionId = activeSessionId.value
  const userMessageId = Date.now()

  pushMessageToSession(requestSessionId, { id: userMessageId, role: 'user', content })
  question.value = ''
  loading.value = true
  abortReason = ''
  pendingSessionId = requestSessionId
  pendingUserMessageId = userMessageId
  const controller = new AbortController()
  sendController = controller

  try {
    const res = await assistantChat({
      question: content,
      wordContext: activeContext.value?.word ? activeContext.value : null,
      history: buildHistory()
    }, {
      signal: controller.signal
    })
    if (!sessionContainsMessage(requestSessionId, userMessageId)) {
      return
    }
    const answer = normalizeAnswer(res?.data?.answer)
    pushMessageToSession(requestSessionId, {
      id: Date.now() + 1,
      role: 'assistant',
      content: answer || '我暂时没有整理出有效回答，你可以换个问法再试一次。',
      autoContinued: isAutoContinued(res?.data?.autoContinued),
      continuationRounds: toSafeContinuationRounds(res?.data?.continuationRounds)
    })
  } catch (error) {
    if (error?.code === 'ERR_CANCELED') {
      if (abortReason === ABORT_REASON_MANUAL_STOP && sessionContainsMessage(requestSessionId, userMessageId)) {
        pushMessageToSession(requestSessionId, {
          id: Date.now() + 1,
          role: 'assistant',
          content: EMPTY_RESPONSE_HINT,
          autoContinued: false,
          continuationRounds: 0
        })
      }
      return
    }
    if (!sessionContainsMessage(requestSessionId, userMessageId)) {
      return
    }
    const rawMessage = String(error?.message || '')
    if (error?.code === 'ECONNABORTED' || rawMessage.includes('timeout')) {
      ElMessage.warning('学习助手响应较慢，请重试一次或缩短问题后再试')
      pushMessageToSession(requestSessionId, {
        id: Date.now() + 1,
        role: 'assistant',
        content: '本次请求超时，未收到模型完整回复。你可以重试一次，或缩短问题后再试。若你当前使用本地模型，请在个人设置中检查“本地模型连通性”。',
        autoContinued: false,
        continuationRounds: 0
      })
    } else {
      ElMessage.warning(error?.businessMessage || error?.message || '学习助手暂时不可用')
      pushMessageToSession(requestSessionId, {
        id: Date.now() + 1,
        role: 'assistant',
        content: '本次请求失败，暂未收到模型回复。请稍后重试；若仍失败，请检查当前模型连接状态。',
        autoContinued: false,
        continuationRounds: 0
      })
    }
  } finally {
    if (sendController === controller) {
      sendController = null
    }
    abortReason = ''
    pendingSessionId = ''
    pendingUserMessageId = null
    loading.value = false
  }
}

function pushMessage(msg) {
  pushMessageToSession(activeSessionId.value, msg)
}

function pushMessageToSession(sessionId, msg) {
  const idx = sessions.value.findIndex((s) => s.id === sessionId)
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

function sessionContainsMessage(sessionId, messageId) {
  if (!sessionId || messageId == null) return false
  const session = sessions.value.find((s) => s.id === sessionId)
  if (!session || !Array.isArray(session.messages)) return false
  return session.messages.some((m) => m?.id === messageId)
}

function buildHistory() {
  return messages.value
    .slice(-6)
    .map((m) => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content }))
}

function buildHistoryFrom(sourceMessages, endExclusive) {
  return sourceMessages
    .slice(0, endExclusive)
    .slice(-6)
    .map((m) => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content }))
}

function isLatestUserMessage(msg) {
  return msg?.role === 'user' && msg?.id === latestUserMessageId.value
}

async function copyMessageContent(content, successText = '已复制') {
  const text = String(content || '')
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successText)
  } catch {
    const area = document.createElement('textarea')
    area.value = text
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    document.body.removeChild(area)
    ElMessage.success(successText)
  }
}

function editUserMessage(messageId) {
  const session = activeSession.value
  if (!session) return
  const target = session.messages.find((m) => m.id === messageId && m.role === 'user')
  if (!target) return
  ElMessageBox.prompt('编辑该条用户消息', '编辑消息', {
    inputValue: String(target.content || ''),
    inputPlaceholder: '请输入消息内容',
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    const next = String(value || '').trim()
    if (!next) {
      ElMessage.warning('消息内容不能为空')
      return
    }
    target.content = next
    const sessionRef = sessions.value.find((s) => s.id === activeSessionId.value)
    if (sessionRef) sessionRef.updatedAt = Date.now()
    persistState()
    ElMessage.success('已更新消息')
  }).catch(() => {})
}

function deleteUserMessage(messageId) {
  const session = activeSession.value
  if (!session) return
  const targetIdx = session.messages.findIndex((m) => m.id === messageId && m.role === 'user')
  if (targetIdx < 0) return
  const target = session.messages[targetIdx]
  if (!target || !isLatestUserMessage(target)) return
  const deletingPendingQuestion = loading.value &&
    !!sendController &&
    pendingSessionId === activeSessionId.value &&
    pendingUserMessageId === messageId

  if (deletingPendingQuestion) {
    abortReason = ABORT_REASON_DELETE_QUESTION
    sendController.abort()
    // Delete the pending user message and any trailing assistant output of this round.
    session.messages = session.messages.slice(0, targetIdx)
    session.updatedAt = Date.now()
    session.hasInteraction = session.messages.some((m) => m?.role === 'user')
    persistState()
    ElMessage.success('已删除该消息并停止AI生成')
    return
  }

  // Delete this user question and all assistant messages linked to it (until next user question).
  const nextUserIdx = session.messages.findIndex((m, idx) => idx > targetIdx && m?.role === 'user')
  const endExclusive = nextUserIdx >= 0 ? nextUserIdx : session.messages.length
  const removedCount = endExclusive - targetIdx
  session.messages = [
    ...session.messages.slice(0, targetIdx),
    ...session.messages.slice(endExclusive)
  ]
  session.updatedAt = Date.now()
  session.hasInteraction = session.messages.some((m) => m?.role === 'user')
  persistState()
  ElMessage.success(removedCount > 1 ? '已删除该问题及对应回答' : '已删除该消息')
}

function setAssistantFeedback(messageId, feedback) {
  const session = activeSession.value
  if (!session) return
  const target = session.messages.find((m) => m.id === messageId && m.role === 'assistant')
  if (!target) return
  target.feedback = target.feedback === feedback ? '' : feedback
  persistState()
}

async function regenerateAssistantMessage(messageId) {
  if (loading.value) return
  const session = activeSession.value
  if (!session) return
  const list = session.messages || []
  const aiIdx = list.findIndex((m) => m.id === messageId && m.role === 'assistant')
  if (aiIdx < 0) return
  let userIdx = -1
  for (let i = aiIdx - 1; i >= 0; i -= 1) {
    if (list[i]?.role === 'user') {
      userIdx = i
      break
    }
  }
  if (userIdx < 0) {
    ElMessage.warning('找不到可重生成的用户提问')
    return
  }

  const userQuestion = String(list[userIdx]?.content || '').trim()
  if (!userQuestion) {
    ElMessage.warning('用户提问为空，无法重生成')
    return
  }

  const history = buildHistoryFrom(list, userIdx + 1)
  regeneratingMessageId.value = String(messageId)
  try {
    const res = await assistantChat({
      question: userQuestion,
      wordContext: activeContext.value?.word ? activeContext.value : null,
      history
    })
    const answer = normalizeAnswer(res?.data?.answer) || '我暂时没有整理出有效回答，你可以稍后再试。'
    list[aiIdx].content = answer
    list[aiIdx].autoContinued = isAutoContinued(res?.data?.autoContinued)
    list[aiIdx].continuationRounds = toSafeContinuationRounds(res?.data?.continuationRounds)
    list[aiIdx].feedback = ''
    session.updatedAt = Date.now()
    persistState()
    ElMessage.success('已重新生成回答')
  } catch (error) {
    ElMessage.warning(error?.businessMessage || error?.message || '重新生成失败，请稍后重试')
  } finally {
    regeneratingMessageId.value = ''
  }
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
      return markdownToPlainText(obj.answer)
    }
  } catch {
    // Ignore.
  }
  if (value.startsWith('{') && value.includes('"answer"')) {
    const m = value.match(/"answer"\s*:\s*"([\s\S]*?)"/)
    if (m && m[1]) {
      return markdownToPlainText(m[1].replace(/\\n/g, '\n').replace(/\\"/g, '"').trim())
    }
  }
  return markdownToPlainText(value)
}

function hasAutoContinuation(msg) {
  return msg?.role === 'assistant' && !!msg?.autoContinued
}

function isAutoContinued(value) {
  return value === true
}

function toSafeContinuationRounds(value) {
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
}

function markdownToPlainText(input) {
  const text = String(input || '')
  if (!text) return ''
  return text
    .replace(/```[\s\S]*?```/g, (m) => m.replace(/```/g, '').trim())
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/[*_]{1,3}([^*_]+)[*_]{1,3}/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function handleMoreCommand(command, session) {
  if (!session) return
  if (command === 'pin') {
    session.pinned = true
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
  if (command === 'remove_group') {
    if (!session.groupId) {
      ElMessage.info('该对话当前未分组')
      return
    }
    session.groupId = null
    persistState()
    ElMessage.success('已删除该对话分组，对话内容保留')
    return
  }
  if (command === 'delete') {
    deleteSession(session.id)
  }
}

function handleGroupManageCommand(command) {
  if (command === 'create') {
    openCreateGroupDialog()
    return
  }
  if (command === 'edit') {
    openEditGroupDialog()
    return
  }
  if (command === 'delete') {
    openDeleteGroupDialog()
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
  renameDialogVisible.value = false
  persistState()
}

async function copySession(session) {
  if (!session) return
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
  await copyMessageContent(lines.join('\n'), '已复制会话全部内容')
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
    const created = ensureGroup(name)
    assignGroupToSession(activeSessionId.value, created.group.id)
    if (created.created) {
      ElMessage.success('分组已创建，并已自动分配到当前会话')
      return
    }
    ElMessage.success('已选择现有分组，并已自动分配到当前会话')
  }).catch(() => {})
}

function createGroupInDialog() {
  const name = newGroupName.value.trim()
  if (!name) {
    ElMessage.warning('请输入分组名称')
    return
  }
  const created = ensureGroup(name)
  const assigned = assignGroupToSession(groupSessionId.value, created.group.id)
  groupTargetId.value = created.group.id
  newGroupName.value = ''
  if (!assigned) {
    if (created.created) {
      ElMessage.success('已创建分组')
    } else {
      ElMessage.success('已选择现有分组')
    }
    return
  }
  groupDialogVisible.value = false
  if (created.created) {
    ElMessage.success('分组已创建，并已自动分配到当前会话')
  } else {
    ElMessage.success('已选择现有分组，并已自动分配到当前会话')
  }
}

function confirmGroupAssign() {
  const session = sessions.value.find((s) => s.id === groupSessionId.value)
  if (!session) {
    groupDialogVisible.value = false
    persistState()
    return
  }
  session.groupId = groupTargetId.value || null
  groupDialogVisible.value = false
  persistState()
  ElMessage.success('分组已更新')
}

function assignGroupToSession(sessionId, groupId) {
  if (!sessionId) return false
  const session = sessions.value.find((s) => s.id === sessionId)
  if (!session) return false
  session.groupId = groupId || null
  persistState()
  return true
}

function openDeleteGroupDialog() {
  if (!groups.value.length) {
    ElMessage.info('暂无可删除分组')
    return
  }
  groupDeleteTargetId.value = ''
  groupDeleteDialogVisible.value = true
}

function confirmDeleteGroup() {
  const targetId = groupDeleteTargetId.value
  if (!targetId) {
    ElMessage.warning('请选择要删除的分组')
    return
  }
  groups.value = groups.value.filter((g) => g.id !== targetId)
  sessions.value.forEach((s) => {
    if (s?.groupId === targetId) {
      s.groupId = null
    }
  })
  if (historyGroupFilter.value === targetId) {
    historyGroupFilter.value = 'all'
  }
  groupDeleteDialogVisible.value = false
  groupDeleteTargetId.value = ''
  persistState()
  ElMessage.success('分组已删除，原分组会话已转为未分组')
}

function openEditGroupDialog() {
  if (!groups.value.length) {
    ElMessage.info('暂无可编辑分组')
    return
  }
  const initial = groupEditTargetId.value && groups.value.some((g) => g.id === groupEditTargetId.value)
    ? groupEditTargetId.value
    : groups.value[0].id
  groupEditTargetId.value = initial
  onGroupEditTargetChange(initial)
  groupEditDialogVisible.value = true
}

function onGroupEditTargetChange(groupId) {
  const group = groups.value.find((g) => g.id === groupId)
  groupEditName.value = group?.name || ''
  groupBulkTargetId.value = ''
}

function confirmRenameGroup() {
  const targetId = groupEditTargetId.value
  if (!targetId) {
    ElMessage.warning('请先选择分组')
    return
  }
  const nextName = String(groupEditName.value || '').trim()
  if (!nextName) {
    ElMessage.warning('分组名称不能为空')
    return
  }
  const duplicated = groups.value.some((g) => g.id !== targetId && normalizeGroupName(g?.name) === normalizeGroupName(nextName))
  if (duplicated) {
    ElMessage.warning('分组名称已存在')
    return
  }
  const group = groups.value.find((g) => g.id === targetId)
  if (!group) {
    ElMessage.warning('分组不存在或已删除')
    return
  }
  group.name = nextName
  persistState()
  ElMessage.success('分组名称已更新')
}

function reassignSessionGroup(sessionId, targetGroupId) {
  const session = sessions.value.find((s) => s?.id === sessionId)
  if (!session) return
  const normalized = targetGroupId || null
  if (session.groupId === normalized) return
  session.groupId = normalized
  persistState()
  ElMessage.success(normalized ? '会话分组已调整' : '会话已移至未分组')
}

function bulkReassignGroupSessions() {
  const sourceGroupId = groupEditTargetId.value
  if (!sourceGroupId) {
    ElMessage.warning('请先选择分组')
    return
  }
  const targetGroupId = groupBulkTargetId.value || null
  if (targetGroupId === sourceGroupId) {
    ElMessage.warning('目标分组不能与当前分组相同')
    return
  }
  const affected = sessions.value.filter((s) => s?.groupId === sourceGroupId)
  if (!affected.length) {
    ElMessage.info('该分组下暂无会话')
    return
  }
  affected.forEach((s) => {
    s.groupId = targetGroupId
  })
  if (historyGroupFilter.value === sourceGroupId) {
    historyGroupFilter.value = targetGroupId || 'none'
  }
  persistState()
  ElMessage.success(targetGroupId ? `已批量转移 ${affected.length} 条会话` : `已将 ${affected.length} 条会话移至未分组`)
}

function groupNameById(groupId) {
  if (!groupId) return ''
  const group = groups.value.find((g) => g.id === groupId)
  return group?.name || ''
}

function normalizeGroupName(value) {
  return String(value || '').trim().toLowerCase()
}

function findGroupByName(name) {
  const target = normalizeGroupName(name)
  if (!target) return null
  return groups.value.find((g) => normalizeGroupName(g?.name) === target) || null
}

function buildGroupId() {
  let id = ''
  do {
    id = `g_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  } while (groups.value.some((g) => g?.id === id))
  return id
}

function buildSessionId() {
  let id = ''
  do {
    id = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  } while (sessions.value.some((s) => s?.id === id))
  return id
}

function ensureGroup(name) {
  const normalized = String(name || '').trim()
  const existing = findGroupByName(normalized)
  if (existing) {
    return { created: false, group: existing }
  }
  const group = {
    id: buildGroupId(),
    name: normalized,
    createdAt: Date.now()
  }
  groups.value.unshift(group)
  persistState()
  return { created: true, group }
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

function loadActiveSessionId() {
  try {
    const value = localStorage.getItem(ACTIVE_SESSION_KEY)
    return value ? String(value) : ''
  } catch {
    return ''
  }
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
    .slice(0, 100)

  const groupsNormalized = groupList
    .filter((g) => g && g.id && g.name)
    .slice(0, 100)

  return { sessions: sessionsNormalized, groups: groupsNormalized }
}

function persistState() {
  const stableSessions = sessions.value
    .filter((s) => s)
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
  localStorage.setItem(ACTIVE_SESSION_KEY, activeSessionId.value || '')
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
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.intro-card,
.chat-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.intro-card {
  position: static;
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
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.intro-card h2 {
  margin: 0 0 8px;
  color: var(--color-primary-strong);
}

.intro-card p {
  margin: 0;
  color: var(--color-muted-strong);
}

.context {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.context-text {
  color: var(--color-muted);
  font-size: 13px;
}

.source-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-size: 12px;
}

.chat-card {
  padding: 14px;
}

.messages {
  min-height: 320px;
  max-height: 58vh;
  overflow: auto;
  padding-top: 8px;
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

.msg.user .msg-body {
  align-items: flex-end;
}

.bubble {
  max-width: 82%;
  white-space: pre-wrap;
  line-height: 1.65;
  padding: 10px 12px;
  border-radius: 10px;
}

.msg.user .bubble {
  background: var(--color-chat-user-bg);
  color: var(--color-chat-user-text);
}

.msg.assistant .bubble {
  background: var(--color-chat-assistant-bg);
  color: var(--color-chat-assistant-text);
}

.msg-meta {
  display: flex;
  padding-left: 4px;
}

.msg-actions {
  display: flex;
  gap: 8px;
  padding-left: 4px;
}

.msg-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 4px;
  min-height: 24px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease;
}

.msg:hover .msg-tools {
  opacity: 1;
  pointer-events: auto;
}

.msg.user .msg-tools {
  justify-content: flex-end;
  padding-left: 0;
  padding-right: 4px;
}

.tool-btn {
  --el-color-primary: #8a6a2f;
  color: var(--color-muted);
}

.tool-btn.active {
  color: #8a6a2f;
}

.tool-btn.danger {
  color: #b4503d;
}

.quick-ask {
  margin: 8px 0 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.composer {
  border-top: 1px solid var(--color-border-soft);
  padding-top: 12px;
}

.actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.actions-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.history-filters {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  margin-bottom: 10px;
}

.history-filters.second-row {
  grid-template-columns: 1fr auto;
  justify-content: space-between;
}

.group-actions {
  display: flex;
  justify-content: flex-end;
}

.group-edit-block {
  margin-bottom: 14px;
}

.group-edit-title {
  font-size: 13px;
  color: var(--color-muted);
  margin-bottom: 8px;
}

.group-edit-rename-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.group-edit-list {
  max-height: 260px;
  overflow: auto;
  border: 1px solid var(--color-border-soft);
  border-radius: 8px;
}

.group-edit-batch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.group-edit-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid var(--color-border-soft);
}

.group-edit-item:last-child {
  border-bottom: 0;
}

.group-edit-session-title {
  font-size: 13px;
  color: var(--color-primary-strong);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-edit-empty {
  color: var(--color-muted);
  font-size: 13px;
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
  border: 1px solid var(--color-border-soft);
  background: var(--color-surface-soft);
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
  color: var(--color-primary-strong);
  font-size: 13px;
}

.history-main .time {
  display: block;
  color: var(--color-muted);
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
  .msg-tools {
    opacity: 1;
    pointer-events: auto;
  }

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
