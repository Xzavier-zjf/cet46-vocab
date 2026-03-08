<template>
  <section class="learn-page">
    <section class="control-card">
      <div class="title-row">
        <h2>浠婃棩瀛︿範</h2>
        <el-button text @click="loadWords">鍒锋柊</el-button>
      </div>

      <div class="control-row">
        <span class="label">璇嶅簱</span>
        <el-radio-group v-model="wordType" @change="loadWords">
          <el-radio-button value="cet4">CET4</el-radio-button>
          <el-radio-button value="cet6">CET6</el-radio-button>
        </el-radio-group>
      </div>

      <div class="summary-row">
        <span>姣忔棩鐩爣 {{ dailyTarget }} 涓</span>
        <span>瀛︿範涓?{{ learningCount }} 涓</span>
        <span>宸插畬鎴?{{ completedCount }} 涓</span>
      </div>
    </section>

    <section v-loading="loading" class="list-card">
      <div v-if="!loading && words.length === 0" class="empty-tip">鏆傛棤鍙涔犲崟璇</div>

      <div v-for="item in words" :key="`${item.wordType}:${item.wordId}`" class="word-item">
        <div class="word-main">
          <strong>{{ item.english }}</strong>
          <span class="phonetic">{{ item.phonetic || '' }}</span>
          <p>{{ item.chinese }}</p>
        </div>

        <div class="word-actions">
          <span v-if="item.isCompleted" class="completed-tag">宸插畬鎴愬涔</span>
          <span v-else-if="item.isLearning" class="learning-tag">瀛︿範涓</span>
          <el-button v-else size="small" :loading="item.adding" @click="addToLearn(item)">鍔犲叆瀛︿範</el-button>
          <el-button text @click="goDetail(item)">瀛︿範璇︽儏</el-button>
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

const normalizeProgressStatus = (item) => {
  const raw = String(item?.progressStatus || '').trim().toUpperCase()
  if (raw === 'COMPLETED' || raw === 'LEARNING' || raw === 'NOT_LEARNING') {
    return raw
  }
  return item?.isLearning ? 'LEARNING' : 'NOT_LEARNING'
}

const mapWordStatus = (list) =>
  list.map((item) => {
    const progressStatus = normalizeProgressStatus(item)
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
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '加载今日学习失败')
  } finally {
    loading.value = false
  }
}

const addToLearn = async (item) => {
  const currentStatus = normalizeProgressStatus(item)
  if (item.adding) return
  if (currentStatus === 'LEARNING') {
    ElMessage.info('璇ュ崟璇嶅凡鍦ㄥ涔犱腑')
    return
  }
  if (currentStatus === 'COMPLETED') {
    ElMessage.info('璇ュ崟璇嶅凡瀹屾垚瀛︿範')
    return
  }

  item.adding = true
  try {
    await request.post('/word/learn/add', { wordId: item.wordId, wordType: item.wordType })
    const statusRes = await request.get('/word/progress/status', {
      params: { wordId: item.wordId, wordType: item.wordType }
    })
    item.progressStatus = normalizeProgressStatus({ progressStatus: statusRes?.data?.status, isLearning: true })
    item.isLearning = item.progressStatus === 'LEARNING'
    item.isCompleted = item.progressStatus === 'COMPLETED'
    ElMessage.success('已加入学习计划')
    await loadWords()
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '鍔犲叆瀛︿範澶辫触')
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
  color: #b79434;
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



