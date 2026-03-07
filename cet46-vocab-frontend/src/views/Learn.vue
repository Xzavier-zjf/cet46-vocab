<template>
  <section class="learn-page">
    <section class="control-card">
      <div class="title-row">
        <h2>今日学习</h2>
        <el-button text @click="loadWords">刷新</el-button>
      </div>

      <div class="control-row">
        <span class="label">词库</span>
        <el-radio-group v-model="wordType" @change="loadWords">
          <el-radio-button value="cet4">CET4</el-radio-button>
          <el-radio-button value="cet6">CET6</el-radio-button>
        </el-radio-group>
      </div>

      <div class="summary-row">
        <span>每日目标 {{ dailyTarget }} 个</span>
        <span>学习中 {{ learningCount }} 个</span>
        <span>已完成 {{ completedCount }} 个</span>
      </div>
    </section>

    <section v-loading="loading" class="list-card">
      <div v-if="!loading && words.length === 0" class="empty-tip">暂无可学习单词</div>

      <div v-for="item in words" :key="`${item.wordType}:${item.wordId}`" class="word-item">
        <div class="word-main">
          <strong>{{ item.english }}</strong>
          <span class="phonetic">{{ item.phonetic || '' }}</span>
          <p>{{ item.chinese }}</p>
        </div>

        <div class="word-actions">
          <span v-if="item.isCompleted" class="completed-tag">已完成学习</span>
          <span v-else-if="item.isLearning" class="learning-tag">学习中</span>
          <el-button v-else size="small" :loading="item.adding" @click="addToLearn(item)">加入学习</el-button>
          <el-button text @click="goDetail(item)">学习详情</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const words = ref([])
const wordType = ref('cet4')

const dailyTarget = computed(() => Number(userStore.dailyTarget || 20))
const learningCount = computed(() => words.value.filter((item) => item.isLearning && !item.isCompleted).length)
const completedCount = computed(() => words.value.filter((item) => item.isCompleted).length)

const mapWordStatus = (list) =>
  list.map((item) => {
    const progressStatus = item?.progressStatus || (item?.isLearning ? 'LEARNING' : 'NOT_LEARNING')
    return {
      ...item,
      progressStatus,
      isLearning: progressStatus === 'LEARNING',
      isCompleted: progressStatus === 'COMPLETED',
      adding: false
    }
  })

const loadWords = async () => {
  loading.value = true
  try {
    const res = await request.get('/word/list', {
      params: {
        type: wordType.value,
        page: 1,
        size: dailyTarget.value
      }
    })
    const list = Array.isArray(res?.data?.list) ? res.data.list : []
    words.value = mapWordStatus(list)
  } finally {
    loading.value = false
  }
}

const addToLearn = async (item) => {
  if (item.isLearning || item.isCompleted || item.adding) return
  item.adding = true
  try {
    await request.post('/word/learn/add', { wordId: item.wordId, wordType: item.wordType })
    const statusRes = await request.get('/word/progress/status', {
      params: { wordId: item.wordId, wordType: item.wordType }
    })
    item.progressStatus = statusRes?.data?.status || 'LEARNING'
    item.isLearning = item.progressStatus === 'LEARNING'
    item.isCompleted = item.progressStatus === 'COMPLETED'
    ElMessage.success('已加入学习计划')
  } finally {
    item.adding = false
  }
}

const goDetail = (item) => {
  router.push(`/words/${item.wordType}/${item.wordId}`)
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  await loadWords()
})
</script>

<style scoped>
.learn-page {
  max-width: 980px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.control-card,
.list-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-row h2 {
  margin: 0;
  color: #1a2b4a;
}

.control-row {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.label {
  color: #6b7a8d;
}

.summary-row {
  margin-top: 10px;
  display: flex;
  gap: 14px;
  color: #4a5f79;
  font-size: 14px;
}

.word-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.word-item + .word-item {
  margin-top: 10px;
}

.word-main strong {
  color: #1a2b4a;
  font-size: 20px;
}

.phonetic {
  margin-left: 8px;
  color: #8da0b8;
}

.word-main p {
  margin: 8px 0 0;
  color: #2c3e50;
}

.word-actions {
  min-width: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.learning-tag {
  color: #8bafd4;
  font-weight: 700;
}

.completed-tag {
  color: #4caf50;
  font-weight: 700;
}

.empty-tip {
  color: #6c7b8f;
  text-align: center;
  padding: 16px 0;
}

@media (max-width: 768px) {
  .summary-row {
    flex-direction: column;
    gap: 6px;
  }

  .word-item {
    flex-direction: column;
  }

  .word-actions {
    align-items: flex-start;
    min-width: 0;
  }
}
</style>
