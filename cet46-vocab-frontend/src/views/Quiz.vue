<template>
  <section class="quiz-page">
    <section v-if="state === 'setup'" class="setup-card">
      <h2>模拟测验设置</h2>

      <div class="setup-row">
        <span class="label">题目数量</span>
        <el-radio-group v-model="setup.count">
          <el-radio-button :label="10">10</el-radio-button>
          <el-radio-button :label="20">20</el-radio-button>
          <el-radio-button :label="30">30</el-radio-button>
        </el-radio-group>
      </div>

      <div class="setup-row">
        <span class="label">模式</span>
        <el-radio-group v-model="setup.mode">
          <el-radio-button label="choice">选择题</el-radio-button>
          <el-radio-button label="fill">填空题</el-radio-button>
        </el-radio-group>
      </div>

      <div class="setup-row">
        <span class="label">词库</span>
        <el-radio-group v-model="setup.wordType">
          <el-radio-button label="cet4">CET4</el-radio-button>
          <el-radio-button label="cet6">CET6</el-radio-button>
          <el-radio-button label="mixed">混合</el-radio-button>
        </el-radio-group>
      </div>

      <el-button class="start-btn" :loading="state === 'loading'" @click="startQuiz">
        开始测验
      </el-button>
    </section>

    <section v-else-if="state === 'loading'" class="state-card center">
      <el-icon class="is-loading loading-icon"><Loading /></el-icon>
      <p>正在生成测验题目...</p>
    </section>

    <section v-else-if="state === 'question' || state === 'answered'" class="question-card">
      <div class="progress">第 {{ currentIndex + 1 }} / 共 {{ questions.length }} 题</div>

      <h3 class="word">{{ currentQuestion?.english || '-' }}</h3>
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
        <el-button class="fill-submit" :disabled="state !== 'question'" @click="submitFill">
          提交答案
        </el-button>
      </div>

      <div v-if="state === 'answered'" class="analysis">
        <h4>解析</h4>
        <p>{{ currentExplanation }}</p>
        <el-button class="next-btn" @click="nextQuestion">下一题</el-button>
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
        <el-button class="again-btn" @click="resetToSetup">再来一次</el-button>
        <el-button @click="goHome">返回首页</el-button>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { generateQuiz, submitQuiz } from '@/api/quiz'

const router = useRouter()

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

    if (!questions.value.length) {
      ElMessage.warning('暂无可用题目，请稍后重试')
      state.value = 'setup'
      return
    }

    state.value = 'question'
    startQuestionTimer()
  } catch (error) {
    state.value = 'setup'
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
    state.value = 'result'
  } catch (error) {
    state.value = 'result'
    result.total = questions.value.length
    result.correct = 0
    result.wrongWords = []
  }
}

const resetToSetup = () => {
  state.value = 'setup'
  quizId.value = ''
  questions.value = []
  currentIndex.value = 0
  answers.value = []
  selectedAnswer.value = ''
  fillAnswer.value = ''
  result.total = 0
  result.correct = 0
  result.wrongWords = []
}

const goHome = () => {
  router.push('/dashboard')
}
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
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.setup-card h2,
.result-card h2 {
  margin: 0 0 18px;
  color: #1A2B4A;
}

.setup-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.label {
  width: 72px;
  color: #5e6e83;
}

.start-btn {
  margin-top: 10px;
  background: #1A2B4A;
  color: #fff;
  border-color: #1A2B4A;
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
  color: #1A2B4A;
}

.progress {
  color: #8896A8;
  font-size: 13px;
}

.word {
  margin: 8px 0 6px;
  color: #1A2B4A;
  font-size: 34px;
}

.phonetic {
  margin: 0 0 16px;
  color: #8da0b8;
}

.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.option-btn {
  min-height: 56px;
  border: 1px solid #dce3ec;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  color: #2C3E50;
  display: flex;
  gap: 10px;
  align-items: center;
}

.option-btn.correct {
  border-color: #4caf50;
  background: #f1fbf1;
}

.option-btn.wrong {
  border-color: #e25d5d;
  background: #fff3f3;
}

.option-btn:disabled {
  cursor: default;
}

.opt-id {
  width: 20px;
  color: #6d7f95;
  font-weight: 700;
}

.fill-area {
  display: flex;
  gap: 10px;
}

.fill-submit {
  border-color: #1A2B4A;
  color: #1A2B4A;
}

.analysis {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #e5eaf2;
}

.analysis h4 {
  margin: 0 0 8px;
  color: #1A2B4A;
}

.analysis p {
  margin: 0;
  color: #4A6FA5;
  line-height: 1.7;
}

.next-btn {
  margin-top: 12px;
}

.score {
  margin: 0 0 16px;
  font-size: 28px;
  color: #1A2B4A;
  font-weight: 700;
}

.wrong-item {
  padding: 10px 0;
  border-bottom: 1px dashed #e5eaf2;
}

.wrong-item:last-child {
  border-bottom: 0;
}

.wrong-item p {
  margin: 0 0 4px;
}

.no-wrong {
  color: #6c7b8f;
}

.result-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

.again-btn {
  background: #1A2B4A;
  color: #fff;
  border-color: #1A2B4A;
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
