<template>
  <section class="learn-page">
    <section class="control-card">
      <div class="title-row">
        <h2>今日学习</h2>
        <el-button text @click="loadWords">刷新</el-button>
      </div>

      <div class="control-row">
        <span class="label">词库类型</span>
        <el-radio-group v-model="wordType" @change="loadWords">
          <el-radio-button v-for="item in WORD_TYPE_OPTIONS_ZH" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="summary-row">
        <span>每日目标 {{ dailyTarget }}</span>
        <span>学习中 {{ learningCount }}</span>
        <span>已完成 {{ completedCount }}</span>
      </div>
    </section>

    <section v-loading="loading" class="list-card">
      <div v-if="!loading && words.length === 0" class="empty-tip">当前词库暂无单词</div>

      <div v-for="item in words" :key="`${item.wordType}:${item.wordId}`" class="word-item">
        <div class="word-main">
          <strong>{{ item.english }}</strong>
          <span class="phonetic">{{ item.phonetic || '' }}</span>
          <p>{{ item.chinese }}</p>
        </div>

        <div class="word-actions">
          <ProgressBadge :status="item.progressStatus" />
          <BtnPrimary
            v-if="item.progressStatus === WORD_PROGRESS.NOT_LEARNING"
            size="small"
            :loading="item.adding"
            @click="addToLearn(item)"
          >
            加入学习
          </BtnPrimary>
          <el-button text @click="goDetail(item)">查看详情</el-button>
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
import { WORD_TYPE_OPTIONS_ZH, WORD_TYPES } from '@/constants/wordTypes'
import { WORD_PROGRESS, normalizeProgressStatus } from '@/constants/wordProgress'
import ProgressBadge from '@/components/common/ProgressBadge.vue'
import BtnPrimary from '@/components/common/BtnPrimary.vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const words = ref([])
const wordType = ref(WORD_TYPES.CET4)

const dailyTarget = computed(() => Number(userStore.dailyTarget || 20))
const learningCount = computed(() => words.value.filter((item) => item.isLearning && !item.isCompleted).length)
const completedCount = computed(() => words.value.filter((item) => item.isCompleted).length)

const mapWordStatus = (list) =>
  list.map((item) => {
    const progressStatus = normalizeProgressStatus(item)
    return {
      ...item,
      progressStatus,
      isLearning: progressStatus === WORD_PROGRESS.LEARNING,
      isCompleted: progressStatus === WORD_PROGRESS.COMPLETED,
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
    ElMessage.error(error?.businessMessage || error?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const addToLearn = async (item) => {
  const currentStatus = normalizeProgressStatus(item)
  if (item.adding) return
  if (currentStatus === WORD_PROGRESS.LEARNING) {
    ElMessage.info('该单词已在学习中')
    return
  }
  if (currentStatus === WORD_PROGRESS.COMPLETED) {
    ElMessage.info('该单词已完成')
    return
  }

  item.adding = true
  try {
    await request.post('/word/learn/add', { wordId: item.wordId, wordType: item.wordType })
    const statusRes = await request.get('/word/progress/status', {
      params: { wordId: item.wordId, wordType: item.wordType }
    })
    item.progressStatus = normalizeProgressStatus({ progressStatus: statusRes?.data?.status, isLearning: true })
    item.isLearning = item.progressStatus === WORD_PROGRESS.LEARNING
    item.isCompleted = item.progressStatus === WORD_PROGRESS.COMPLETED
    ElMessage.success('已加入学习')
    await loadWords()
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '加入学习失败')
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
  background: var(--color-surface);
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
  color: var(--color-primary-strong);
}

.control-row {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.label {
  color: var(--color-muted);
}

.summary-row {
  margin-top: 10px;
  display: flex;
  gap: 14px;
  color: var(--color-muted-strong);
  font-size: 14px;
}

.word-item {
  border: 1px solid var(--color-border-soft);
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
  color: var(--color-primary-strong);
  font-size: 20px;
}

.phonetic {
  margin-left: 8px;
  color: var(--color-muted-soft);
}

.word-main p {
  margin: 8px 0 0;
  color: var(--color-text);
}

.word-actions {
  min-width: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.empty-tip {
  color: var(--color-muted);
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


