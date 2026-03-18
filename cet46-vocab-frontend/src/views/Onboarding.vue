<template>
  <section class="onboarding-page">
    <section class="panel">
      <h2>欢迎使用，先做一个学习风格小测试</h2>

      <article v-for="(q, qIdx) in questions" :key="qIdx" class="question-card">
        <h3>{{ q.title }}</h3>
        <div class="options">
          <button
            v-for="opt in q.options"
            :key="opt.style"
            class="option-btn"
            :class="{ active: answers[qIdx] === opt.style }"
            @click="answers[qIdx] = opt.style"
          >
            {{ opt.text }}
          </button>
        </div>
      </article>

      <BtnPrimary class="submit-btn" :loading="submitting" @click="submitTest">完成并进入首页</BtnPrimary>

      <section class="provider-section">
        <h3>AI来源</h3>
        <div class="provider-grid">
          <button
            v-for="item in providers"
            :key="item.value"
            class="option-btn"
            :class="{ active: llmProvider === item.value }"
            @click="llmProvider = item.value"
          >
            {{ item.text }}
          </button>
        </div>
      </section>
    </section>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import BtnPrimary from '@/components/common/BtnPrimary.vue'

const router = useRouter()
const userStore = useUserStore()
const submitting = ref(false)
const llmProvider = ref(userStore.llmProvider || 'local')

const questions = [
  {
    title: '1. 你更喜欢怎样的记忆提示？',
    options: [
      { style: 'academic', text: '解释词源和语法逻辑' },
      { style: 'story', text: '讲一个容易记住的小故事' },
      { style: 'sarcastic', text: '来一句扎心但忘不掉的话' }
    ]
  },
  {
    title: '2. 遇到生词时你最希望看到？',
    options: [
      { style: 'academic', text: '清晰定义和正式例句' },
      { style: 'story', text: '生活化场景联想' },
      { style: 'sarcastic', text: '反差强烈的吐槽例句' }
    ]
  },
  {
    title: '3. 你希望系统的语气更偏向？',
    options: [
      { style: 'academic', text: '专业严谨' },
      { style: 'story', text: '温和叙事' },
      { style: 'sarcastic', text: '犀利直接' }
    ]
  }
]

const answers = reactive(['', '', ''])
const providers = [
  { value: 'local', text: '本地模型 (Ollama)' },
  { value: 'cloud', text: '云端API' }
]

const calcStyle = () => {
  const count = { academic: 0, story: 0, sarcastic: 0 }
  answers.forEach((s) => {
    if (count[s] != null) count[s] += 1
  })
  const order = ['academic', 'story', 'sarcastic']
  let best = 'story'
  let bestVal = -1
  order.forEach((style) => {
    if (count[style] > bestVal) {
      best = style
      bestVal = count[style]
    }
  })
  return best
}

const submitTest = async () => {
  if (answers.some((s) => !s)) {
    ElMessage.warning('请先完成全部三题')
    return
  }

  const style = calcStyle()
  submitting.value = true
  const dailyTarget = userStore.dailyTarget || 20
  try {
    await request.put('/user/preference', {
      llmStyle: style,
      llmProvider: llmProvider.value,
      dailyTarget
    })
    userStore.llmStyle = style
    userStore.llmProvider = llmProvider.value
    userStore.dailyTarget = dailyTarget
    ElMessage.success('学习风格已设置')
    router.push('/dashboard')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  background: var(--bg-gradient-1), var(--bg-gradient-2), var(--color-bg);
  display: grid;
  place-items: center;
  padding: 24px;
}

.panel {
  width: min(920px, 100%);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  box-shadow: var(--shadow-card);
  padding: 28px;
}

.panel h2 {
  margin: 0 0 18px;
  color: var(--color-primary-strong);
}

.question-card {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid var(--color-border-soft);
  border-radius: 14px;
}

.question-card h3 {
  margin: 0 0 10px;
  color: var(--color-text);
  font-size: 16px;
}

.options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.option-btn {
  border: 1px solid var(--color-border-soft);
  border-radius: 10px;
  background: var(--color-surface);
  padding: 10px;
  cursor: pointer;
  color: var(--color-muted-strong);
  text-align: left;
}

.option-btn.active {
  border-color: var(--color-accent);
  background: var(--color-warning-soft);
  color: var(--color-primary-strong);
}

.submit-btn {
  margin-top: 10px;
  font-weight: 700;
}

.provider-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-soft);
}

.provider-section h3 {
  margin: 0 0 10px;
  color: var(--color-text);
  font-size: 16px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 900px) {
  .options {
    grid-template-columns: 1fr;
  }

  .provider-grid {
    grid-template-columns: 1fr;
  }
}
</style>
