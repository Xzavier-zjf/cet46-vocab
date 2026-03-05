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

      <el-button class="submit-btn" :loading="submitting" @click="submitTest">完成并进入首页</el-button>
    </section>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const submitting = ref(false)

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
      dailyTarget
    })
    userStore.llmStyle = style
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
  background: linear-gradient(145deg, #F5F7FA 0%, #ECF2FB 100%);
  display: grid;
  place-items: center;
  padding: 24px;
}

.panel {
  width: min(920px, 100%);
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  box-shadow: var(--shadow-card);
  padding: 28px;
}

.panel h2 {
  margin: 0 0 18px;
  color: #1A2B4A;
}

.question-card {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #e4eaf2;
  border-radius: 14px;
}

.question-card h3 {
  margin: 0 0 10px;
  color: #2C3E50;
  font-size: 16px;
}

.options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.option-btn {
  border: 1px solid #d9e1ec;
  border-radius: 10px;
  background: #fff;
  padding: 10px;
  cursor: pointer;
  color: #42566f;
  text-align: left;
}

.option-btn.active {
  border-color: #C9A84C;
  background: rgba(201, 168, 76, 0.12);
  color: #1A2B4A;
}

.submit-btn {
  margin-top: 10px;
  background: #1A2B4A;
  border-color: #1A2B4A;
  color: #fff;
}

@media (max-width: 900px) {
  .options {
    grid-template-columns: 1fr;
  }
}
</style>
