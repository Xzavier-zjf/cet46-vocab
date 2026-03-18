<template>
  <section class="quiz-page">
    <section v-if="state === 'setup'" class="setup-card">
      <h2>模拟测验设置</h2>

      <div class="setup-row">
        <span class="label">题目数量</span>
        <el-radio-group v-model="setup.count">
          <el-radio-button :value="10">10</el-radio-button>
          <el-radio-button :value="20">20</el-radio-button>
          <el-radio-button :value="30">30</el-radio-button>
        </el-radio-group>
      </div>

      <div class="setup-row">
        <span class="label">模式</span>
        <el-radio-group v-model="setup.mode">
          <el-radio-button value="choice">选择题</el-radio-button>
          <el-radio-button value="fill">填空题</el-radio-button>
        </el-radio-group>
      </div>

      <div class="setup-row">
        <span class="label">词库</span>
        <el-radio-group v-model="setup.wordType">
          <el-radio-button value="cet4">CET4</el-radio-button>
          <el-radio-button value="cet6">CET6</el-radio-button>
          <el-radio-button value="mixed">混合</el-radio-button>
        </el-radio-group>
      </div>

      <BtnPrimary class="start-btn" :loading="state === 'loading'" @click="startQuiz">
        开始测验
      </BtnPrimary>

      <div class="history-card">
        <div class="history-title">测验历史</div>
        <div v-if="quizHistory.length" class="history-list">
          <div
            v-for="item in quizHistory"
            :key="item.id"
            class="history-item"
            role="button"
            tabindex="0"
            @click="openHistoryDetail(item)"
            @keyup.enter="openHistoryDetail(item)"
          >
            <div class="history-line">
              <span class="history-time">{{ formatHistoryTime(item.finishedAt) }}</span>
              <span class="history-score">{{ item.correct }}/{{ item.total }}</span>
            </div>
            <div class="history-meta">
              {{ String(item.wordType || '').toUpperCase() }} /
              {{ item.mode === 'choice' ? '选择题' : '填空题' }} /
              {{ item.count }} 题
              <span class="history-wrong">错题 {{ item.wrongCount }}</span>
            </div>
            <div class="history-action">点击查看当次作答详情</div>
          </div>
        </div>
        <div v-else class="history-empty">暂无历史记录</div>
      </div>
    </section>

    <section v-else-if="state === 'loading'" class="state-card center">
      <el-icon class="is-loading loading-icon"><Loading /></el-icon>
      <p>正在生成测验题目...</p>
    </section>

    <section v-else-if="state === 'question' || state === 'answered'" class="question-card">
      <div class="question-header">
        <div class="progress">第 {{ currentIndex + 1 }} / 共 {{ questions.length }} 题</div>
        <el-button text class="back-setup-btn" @click="backToSetup">返回设置</el-button>
      </div>

      <div class="word-row">
        <h3 class="word">{{ currentQuestion?.english || '-' }}</h3>
        <div class="speak-actions">
          <button class="quick-speak" @click="handleSpeak(currentQuestion?.english, 'uk')">英音</button>
          <button class="quick-speak" @click="handleSpeak(currentQuestion?.english, 'us')">美音</button>
        </div>
      </div>
      <p class="phonetic">{{ currentQuestion?.phonetic || '' }}</p>

      <div v-if="isChoiceMode" class="choice-grid">
        <button
          v-for="opt in currentQuestion?.options || []"
          :key="opt.id"
          class="option-btn"
          :class="optionClass(opt.id)"
          :disabled="state !== 'question'"
          @click="selectChoice(opt.id)"
        >
          <span class="opt-id">{{ opt.id }}</span>
          <span>{{ opt.text }}</span>
        </button>
      </div>

      <div v-else class="fill-area">
        <el-input
          v-model="fillAnswer"
          placeholder="请输入答案"
          :disabled="state !== 'question'"
          @keyup.enter="submitFill"
        />
        <BtnPrimary class="fill-submit" :disabled="state !== 'question'" @click="submitFill">
          提交答案
        </BtnPrimary>
      </div>

      <div v-if="state === 'answered'" class="analysis">
        <h4>解析</h4>
        <p>{{ currentExplanation }}</p>
        <BtnPrimary class="next-btn" @click="nextQuestion">下一题</BtnPrimary>
      </div>
    </section>

    <section v-else-if="state === 'submitting'" class="state-card center">
      <el-icon class="is-loading loading-icon"><Loading /></el-icon>
      <p>正在提交测验结果...</p>
    </section>

    <section v-else-if="state === 'result'" class="result-card">
      <h2>测验结果</h2>
      <p class="score">{{ result.correct }}/{{ result.total }} 正确</p>

      <el-collapse>
        <el-collapse-item title="错题列表" name="wrong">
          <div v-if="!result.wrongWords.length" class="no-wrong">本次没有错题</div>
          <div v-for="(item, idx) in result.wrongWords" :key="idx" class="wrong-item">
            <p><strong>{{ item.english }}</strong></p>
            <p>正确答案：{{ item.correctAnswer }}</p>
            <p>你的答案：{{ item.userAnswer || '-' }}</p>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div class="result-actions">
        <BtnPrimary class="again-btn" @click="resetToSetup">再来一次</BtnPrimary>
        <BtnSecondary @click="goHome">返回首页</BtnSecondary>
      </div>
    </section>

    <el-dialog
      v-model="historyDetailVisible"
      title="测验历史详情"
      width="860px"
      append-to-body
    >
      <template v-if="historyDetailRecord">
        <p class="history-detail-summary">
          {{ formatHistoryTime(historyDetailRecord.finishedAt) }} ·
          {{ String(historyDetailRecord.wordType || '').toUpperCase() }} ·
          {{ historyDetailRecord.mode === 'choice' ? '选择题' : '填空题' }} ·
          得分 {{ historyDetailRecord.correct }}/{{ historyDetailRecord.total }}
        </p>

        <el-table
          v-if="Array.isArray(historyDetailRows) && historyDetailRows.length"
          :data="historyDetailRows"
          size="small"
          max-height="420"
        >
          <el-table-column prop="index" label="#" width="60" />
          <el-table-column prop="english" label="单词" min-width="140" />
          <el-table-column prop="userAnswer" label="你的答案" min-width="180" />
          <el-table-column prop="correctAnswer" label="正确答案" min-width="180" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isCorrect ? 'success' : 'danger'" effect="plain">
                {{ row.isCorrect ? '正确' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="history-empty">该历史记录暂无题目级详情（旧版本记录）。</div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { generateQuiz, submitQuiz } from '@/api/quiz'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/token'
import { speakWord } from '@/utils/speech'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

const router = useRouter()
const userStore = useUserStore()

const QUIZ_SESSION_STORAGE_PREFIX = 'quiz:session:'
const QUIZ_HISTORY_STORAGE_PREFIX = 'quiz:history:'
const QUIZ_HISTORY_LIMIT = 20
const allowedStates = new Set(['setup', 'loading', 'question', 'answered', 'submitting', 'result'])

const state = ref('setup')
const setup = reactive({
  count: 10,
  mode: 'choice',
  wordType: 'cet4'
})

const quizId = ref('')
const questions = ref([])
const currentIndex = ref(0)
const answers = ref([])
const questionStartAt = ref(0)

const selectedAnswer = ref('')
const fillAnswer = ref('')
const quizHistory = ref([])
const historyDetailVisible = ref(false)
const historyDetailRecord = ref(null)

const result = reactive({
  total: 0,
  correct: 0,
  wrongWords: []
})

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const isChoiceMode = computed(() => (currentQuestion.value?.mode || setup.mode) === 'choice')
const currentExplanation = computed(() => {
  const q = currentQuestion.value || {}
  return q.llmSentence || q.exampleSentence || q.explanation || '暂无解析'
})
const historyDetailRows = computed(() => historyDetailRecord.value?.details || [])

const getCorrectChoiceId = (q) => q?.correctId || ''

const optionClass = (optionId) => {
  if (state.value !== 'answered') return ''
  const q = currentQuestion.value
  const correctId = getCorrectChoiceId(q)
  if (optionId === correctId) return 'correct'
  if (optionId === selectedAnswer.value && selectedAnswer.value !== correctId) return 'wrong'
  return ''
}

const startQuestionTimer = () => {
  questionStartAt.value = Date.now()
}

const calcTimeSpent = () => Math.max(Date.now() - questionStartAt.value, 0)

const getQuizSessionStorageKey = () => {
  const token = getToken() || ''
  return `${QUIZ_SESSION_STORAGE_PREFIX}${userStore.userId || token || 'anonymous'}`
}

const getQuizHistoryStorageKey = () => {
  const token = getToken() || ''
  return `${QUIZ_HISTORY_STORAGE_PREFIX}${userStore.userId || token || 'anonymous'}`
}

const resetRuntime = () => {
  quizId.value = ''
  questions.value = []
  currentIndex.value = 0
  answers.value = []
  selectedAnswer.value = ''
  fillAnswer.value = ''
  questionStartAt.value = 0
  result.total = 0
  result.correct = 0
  result.wrongWords = []
}

const snapshot = () => ({
  state: state.value,
  setup: { ...setup },
  quizId: quizId.value,
  questions: questions.value,
  currentIndex: currentIndex.value,
  answers: answers.value,
  questionStartAt: questionStartAt.value,
  selectedAnswer: selectedAnswer.value,
  fillAnswer: fillAnswer.value,
  result: { ...result }
})

const persistSession = () => {
  localStorage.setItem(getQuizSessionStorageKey(), JSON.stringify(snapshot()))
}

const persistHistory = () => {
  localStorage.setItem(getQuizHistoryStorageKey(), JSON.stringify(quizHistory.value))
}

const restoreHistory = () => {
  const raw = localStorage.getItem(getQuizHistoryStorageKey())
  if (!raw) {
    quizHistory.value = []
    return
  }
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      quizHistory.value = parsed
        .filter((item) => item && item.id)
        .slice(0, QUIZ_HISTORY_LIMIT)
    } else {
      quizHistory.value = []
    }
  } catch {
    quizHistory.value = []
    localStorage.removeItem(getQuizHistoryStorageKey())
  }
}

const formatHistoryTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString()
}

const normalizeAnswer = (value) => String(value || '').trim().toLowerCase()

const resolveChoiceText = (question, choiceId) => {
  if (!question || !Array.isArray(question.options)) return String(choiceId || '-')
  const found = question.options.find((opt) => String(opt?.id || '') === String(choiceId || ''))
  return found?.text || String(choiceId || '-')
}

const buildHistoryDetails = () => {
  const answerMap = new Map(
    answers.value
      .filter((item) => item?.questionId)
      .map((item) => [String(item.questionId), item.userAnswer])
  )

  return questions.value.map((question, idx) => {
    const questionId = String(question?.questionId || '')
    const mode = question?.mode || setup.mode
    const userRaw = answerMap.get(questionId) ?? ''
    const correctRaw = mode === 'choice' ? getCorrectChoiceId(question) : (question?.correctAnswer || '')
    const userAnswer = mode === 'choice' ? resolveChoiceText(question, userRaw) : String(userRaw || '-')
    const correctAnswer = mode === 'choice' ? resolveChoiceText(question, correctRaw) : String(correctRaw || '-')
    const isCorrect = mode === 'choice'
      ? String(userRaw || '') === String(correctRaw || '')
      : normalizeAnswer(userRaw) === normalizeAnswer(correctRaw)

    return {
      index: idx + 1,
      english: question?.english || '-',
      userAnswer,
      correctAnswer,
      isCorrect
    }
  })
}

const pushHistoryRecord = () => {
  const record = {
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    finishedAt: new Date().toISOString(),
    wordType: setup.wordType,
    mode: setup.mode,
    count: questions.value.length,
    total: result.total,
    correct: result.correct,
    wrongCount: Math.max(result.total - result.correct, 0),
    details: buildHistoryDetails()
  }
  quizHistory.value = [record, ...quizHistory.value].slice(0, QUIZ_HISTORY_LIMIT)
  persistHistory()
}

const openHistoryDetail = (record) => {
  historyDetailRecord.value = record || null
  historyDetailVisible.value = true
}

const normalizeRestoredState = () => {
  if (!questions.value.length && ['question', 'answered', 'submitting'].includes(state.value)) {
    state.value = 'setup'
    resetRuntime()
  }
  if (currentIndex.value < 0 || currentIndex.value >= questions.value.length) {
    currentIndex.value = 0
  }
}

const restoreSession = () => {
  const raw = localStorage.getItem(getQuizSessionStorageKey())
  if (!raw) return
  try {
    const parsed = JSON.parse(raw)
    state.value = allowedStates.has(parsed?.state) ? parsed.state : 'setup'

    setup.count = Number(parsed?.setup?.count || 10)
    setup.mode = parsed?.setup?.mode || 'choice'
    setup.wordType = parsed?.setup?.wordType || 'cet4'

    quizId.value = parsed?.quizId || ''
    questions.value = Array.isArray(parsed?.questions) ? parsed.questions : []
    currentIndex.value = Number(parsed?.currentIndex || 0)
    answers.value = Array.isArray(parsed?.answers) ? parsed.answers : []
    questionStartAt.value = Number(parsed?.questionStartAt || 0)

    selectedAnswer.value = parsed?.selectedAnswer || ''
    fillAnswer.value = parsed?.fillAnswer || ''

    result.total = Number(parsed?.result?.total || 0)
    result.correct = Number(parsed?.result?.correct || 0)
    result.wrongWords = Array.isArray(parsed?.result?.wrongWords) ? parsed.result.wrongWords : []

    normalizeRestoredState()
  } catch {
    state.value = 'setup'
    resetRuntime()
    localStorage.removeItem(getQuizSessionStorageKey())
  }
}

const startQuiz = async () => {
  state.value = 'loading'
  try {
    const res = await generateQuiz({
      count: setup.count,
      mode: setup.mode,
      wordType: setup.wordType
    })

    quizId.value = res?.data?.quizId || ''
    questions.value = Array.isArray(res?.data?.questions) ? res.data.questions : []
    currentIndex.value = 0
    answers.value = []
    selectedAnswer.value = ''
    fillAnswer.value = ''
    result.total = 0
    result.correct = 0
    result.wrongWords = []

    if (!questions.value.length) {
      ElMessage.warning('暂无可用题目，请稍后重试')
      state.value = 'setup'
      resetRuntime()
      return
    }

    state.value = 'question'
    startQuestionTimer()
  } catch {
    state.value = 'setup'
    resetRuntime()
  }
}

const selectChoice = (optionId) => {
  if (state.value !== 'question') return
  selectedAnswer.value = optionId
  answers.value.push({
    questionId: currentQuestion.value?.questionId,
    userAnswer: optionId,
    timeSpentMs: calcTimeSpent()
  })
  state.value = 'answered'
}

const submitFill = () => {
  if (state.value !== 'question') return
  const value = fillAnswer.value.trim()
  if (!value) {
    ElMessage.warning('请输入答案')
    return
  }
  selectedAnswer.value = value
  answers.value.push({
    questionId: currentQuestion.value?.questionId,
    userAnswer: value,
    timeSpentMs: calcTimeSpent()
  })
  state.value = 'answered'
}

const nextQuestion = async () => {
  if (currentIndex.value + 1 < questions.value.length) {
    currentIndex.value += 1
    state.value = 'question'
    selectedAnswer.value = ''
    fillAnswer.value = ''
    startQuestionTimer()
    return
  }

  state.value = 'submitting'
  try {
    const res = await submitQuiz({
      quizId: quizId.value,
      answers: answers.value
    })
    result.total = Number(res?.data?.total || questions.value.length)
    result.correct = Number(res?.data?.correct || 0)
    result.wrongWords = Array.isArray(res?.data?.wrongWords) ? res.data.wrongWords : []
    pushHistoryRecord()
    state.value = 'result'
  } catch {
    result.total = questions.value.length
    result.correct = 0
    result.wrongWords = []
    pushHistoryRecord()
    state.value = 'result'
  }
}

const resetToSetup = () => {
  state.value = 'setup'
  resetRuntime()
}

const backToSetup = () => {
  state.value = 'setup'
  resetRuntime()
}

const goHome = () => {
  router.push('/dashboard')
}

const handleSpeak = (word, accent) => {
  const result = speakWord(word, accent)
  if (result.ok) return
  if (result.reason === 'unsupported') {
    ElMessage.warning('当前浏览器不支持语音播放')
  }
}

watch(snapshot, persistSession, { deep: true })

onMounted(() => {
  restoreHistory()
  restoreSession()
})
</script>

<style scoped>
.quiz-page {
  max-width: 980px;
  margin: 0 auto;
}

.setup-card,
.question-card,
.state-card,
.result-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.setup-card h2,
.result-card h2 {
  margin: 0 0 18px;
  color: var(--color-primary-strong);
}

.setup-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.label {
  width: 72px;
  color: var(--color-muted);
}

.start-btn {
  margin-top: 10px;
  font-weight: 700;
}

.history-card {
  margin-top: 16px;
  border-top: 1px dashed var(--color-border-dashed);
  padding-top: 12px;
}

.history-title {
  font-weight: 700;
  color: var(--color-primary-strong);
  margin-bottom: 8px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
}

.history-item {
  cursor: pointer;
  border: 1px solid var(--color-border-soft);
  border-radius: 8px;
  padding: 8px 10px;
}

.history-item:hover {
  border-color: var(--color-accent);
  background: var(--color-warning-soft);
}

.history-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--color-muted-strong);
  font-size: 13px;
}

.history-score {
  font-weight: 700;
  color: var(--color-primary-strong);
}

.history-meta {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 12px;
}

.history-wrong {
  margin-left: 10px;
}

.history-action {
  margin-top: 6px;
  color: var(--color-warning);
  font-size: 12px;
}

.history-detail-summary {
  margin: 0 0 10px;
  color: var(--color-muted);
  font-size: 13px;
}

.history-empty {
  color: var(--color-muted);
  font-size: 13px;
}

.center {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
}

.loading-icon {
  font-size: 32px;
  color: var(--color-primary-strong);
}

.question-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.progress {
  color: var(--color-muted-soft);
  font-size: 13px;
}

.back-setup-btn {
  color: var(--color-muted);
}

.word {
  margin: 8px 0 6px;
  color: var(--color-primary-strong);
  font-size: 34px;
}

.word-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.speak-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-speak {
  border: 1px solid var(--color-border-soft);
  background: var(--color-surface);
  color: var(--color-muted-strong);
  border-radius: 12px;
  font-size: 12px;
  padding: 2px 8px;
  cursor: pointer;
}

.phonetic {
  margin: 0 0 16px;
  color: var(--color-muted-soft);
}

.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.option-btn {
  min-height: 56px;
  border: 1px solid var(--color-border-soft);
  background: var(--color-surface);
  border-radius: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  color: #2c3e50;
  display: flex;
  gap: 10px;
  align-items: center;
}

.option-btn.correct {
  border-color: var(--color-success);
  background: var(--color-success-soft);
}

.option-btn.wrong {
  border-color: var(--color-danger);
  background: var(--color-danger-soft);
}

.option-btn:disabled {
  cursor: default;
}

.opt-id {
  width: 20px;
  color: var(--color-muted);
  font-weight: 700;
}

.fill-area {
  display: flex;
  gap: 10px;
}

.fill-submit {
  font-weight: 700;
}

.analysis {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border-dashed);
}

.analysis h4 {
  margin: 0 0 8px;
  color: var(--color-primary-strong);
}

.analysis p {
  margin: 0;
  color: var(--color-warning);
  line-height: 1.7;
}

.next-btn {
  margin-top: 12px;
}

.score {
  margin: 0 0 16px;
  font-size: 28px;
  color: var(--color-primary-strong);
  font-weight: 700;
}

.wrong-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border-dashed);
}

.wrong-item:last-child {
  border-bottom: 0;
}

.wrong-item p {
  margin: 0 0 4px;
}

.no-wrong {
  color: var(--color-muted);
}

.result-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

.again-btn {
  font-weight: 700;
}

@media (max-width: 768px) {
  .choice-grid {
    grid-template-columns: 1fr;
  }

  .fill-area {
    flex-direction: column;
  }
}
</style>

