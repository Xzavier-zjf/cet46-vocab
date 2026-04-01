<template>
  <section class="word-list-page">
    <div class="filter-bar">
      <el-radio-group v-model="query.type" @change="onFilterChange">
        <el-radio-button v-for="item in WORD_TYPE_OPTIONS_ZH" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
      </el-radio-group>

      <el-input
        v-model="keywordInput"
        class="search-input"
        clearable
        placeholder="搜索英文单词"
        @clear="onKeywordClear"
      />
      <el-button v-if="hasKeyword" text class="reset-search-btn" @click="resetKeywordSearch">
        重置
      </el-button>

      <el-select v-model="query.pos" class="pos-select" placeholder="词性" @change="onFilterChange">
        <el-option label="全部词性" value="" />
        <el-option label="v." value="v" />
        <el-option label="n." value="n" />
        <el-option label="adj." value="adj" />
        <el-option label="adv." value="adv" />
        <el-option label="其他" value="other" />
      </el-select>

      <BtnSecondary class="retry-pending-btn" :loading="retryPendingLoading" @click="retryPendingBatch">
        批量重试 AI
      </BtnSecondary>
    </div>

    <el-table v-loading="loading" :data="tableData" class="word-table" border>
      <el-table-column label="单词" min-width="200">
        <template #default="scope">
          <div class="word-cell">
            <button class="word-link" @click="goDetail(scope.row)">{{ scope.row.english }}</button>
            <button
              class="quick-speak"
              title="播放英式发音"
              aria-label="播放英式发音"
              @click="handleSpeak(scope.row.english, 'uk')"
            >
              英音
            </button>
            <button
              class="quick-speak"
              title="播放美式发音"
              aria-label="播放美式发音"
              @click="handleSpeak(scope.row.english, 'us')"
            >
              美音
            </button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="音标" prop="phonetic" min-width="160" />
      <el-table-column label="中文释义" prop="chinese" min-width="320" show-overflow-tooltip />
      <el-table-column label="词性" prop="pos" width="110" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="scope">
          <div class="operation-stack">
            <ProgressBadge :status="scope.row.progressStatus" />
            <BtnPrimary
              v-if="scope.row.progressStatus === WORD_PROGRESS.NOT_LEARNING"
              size="small"
              :loading="scope.row.adding"
              @click="addToLearn(scope.row)"
            >
              加入学习
            </BtnPrimary>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        :current-page="pagination.page"
        :page-size="pagination.size"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { speakWord } from '@/utils/speech'
import { useDashboardStore } from '@/stores/dashboard'
import { WORD_TYPE_OPTIONS_ZH, WORD_TYPES, normalizeWordType } from '@/constants/wordTypes'
import { WORD_PROGRESS, normalizeProgressStatus } from '@/constants/wordProgress'
import ProgressBadge from '@/components/common/ProgressBadge.vue'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

const router = useRouter()
const route = useRoute()
const dashboardStore = useDashboardStore()

const loading = ref(false)
const retryPendingLoading = ref(false)
const tableData = ref([])

const query = reactive({
  type: WORD_TYPES.CET4,
  keyword: '',
  pos: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const keywordInput = ref('')
const hasKeyword = computed(() => keywordInput.value.trim().length > 0 || query.keyword.length > 0)
let searchTimer = null
let listController = null

const parsePositiveInt = (value, fallback) => {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

const normalizeType = (value) => normalizeWordType(value, WORD_TYPES.CET4)

const syncStateFromRoute = () => {
  query.type = normalizeType(route.query.type)
  query.keyword = String(route.query.keyword || '').trim()
  query.pos = String(route.query.pos || '').trim()
  keywordInput.value = query.keyword
  pagination.page = parsePositiveInt(route.query.page, 1)
  pagination.size = parsePositiveInt(route.query.size, 10)
}

const syncRouteFromState = async () => {
  const nextQuery = {
    type: query.type,
    page: String(pagination.page),
    size: String(pagination.size)
  }
  if (query.keyword) nextQuery.keyword = query.keyword
  if (query.pos) nextQuery.pos = query.pos
  const sameQuery = JSON.stringify(route.query) === JSON.stringify(nextQuery)
  if (sameQuery) return
  await router.replace({ path: '/words', query: nextQuery })
}

const normalizePos = (value) => {
  if (!value || value === 'other') return ''
  return value
}

const isOtherPos = (posValue) => {
  if (!posValue) return true
  const set = new Set(['v', 'n', 'adj', 'adv'])
  const parts = String(posValue)
    .split(',')
    .map((p) => p.trim().toLowerCase())
    .filter(Boolean)
  if (!parts.length) return true
  return parts.every((part) => !set.has(part))
}

const loadList = async () => {
  if (listController) {
    listController.abort()
  }
  listController = new AbortController()
  loading.value = true
  try {
    const requestedPage = pagination.page
    const requestedSize = pagination.size
    const params = {
      type: query.type,
      page: requestedPage,
      size: requestedSize,
      keyword: query.keyword || undefined,
      pos: normalizePos(query.pos) || undefined
    }
    const res = await request.get('/word/list', { params, signal: listController.signal })
    const list = Array.isArray(res?.data?.list) ? res.data.list : []
    let renderList = list
    let total = Number(res?.data?.total || 0)

    if (list.length > requestedSize) {
      total = list.length
      const start = (requestedPage - 1) * requestedSize
      const end = start + requestedSize
      renderList = list.slice(start, end)
    }

    const mapped = renderList.map((item) => ({
      ...item,
      progressStatus: normalizeProgressStatus(item),
      adding: false
    }))

    tableData.value = query.pos === 'other' ? mapped.filter((item) => isOtherPos(item.pos)) : mapped
    pagination.total = total
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      ElMessage.error(error?.businessMessage || error?.message || '加载失败')
    }
  } finally {
    if (!listController?.signal?.aborted) {
      loading.value = false
    }
  }
}

const onFilterChange = () => {
  pagination.page = 1
  syncRouteFromState()
  loadList()
}

const onPageChange = (page) => {
  pagination.page = page
  syncRouteFromState()
  loadList()
}

const onSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  syncRouteFromState()
  loadList()
}

const onKeywordClear = () => {
  query.keyword = ''
  keywordInput.value = ''
  pagination.page = 1
  syncRouteFromState()
  loadList()
}

const resetKeywordSearch = () => onKeywordClear()

const handleSpeak = (word, accent) => {
  const result = speakWord(word, accent)
  if (result.ok) return
  if (result.reason === 'unsupported') {
    ElMessage.warning('当前浏览器不支持语音播放')
  }
}

const goDetail = (row) => {
  router.push({
    path: `/words/${row.wordType}/${row.wordId}`,
    query: { from: route.fullPath }
  })
}

const addToLearn = async (row) => {
  const currentStatus = normalizeProgressStatus(row)
  if (row.adding) return
  if (currentStatus === WORD_PROGRESS.LEARNING) {
    ElMessage.info('该单词已在学习中')
    return
  }
  if (currentStatus === WORD_PROGRESS.COMPLETED) {
    ElMessage.info('该单词已完成')
    return
  }

  row.adding = true
  try {
    await request.post('/word/learn/add', { wordId: row.wordId, wordType: row.wordType })
    const statusRes = await request.get('/word/progress/status', {
      params: { wordId: row.wordId, wordType: row.wordType }
    })
    row.progressStatus = normalizeProgressStatus({ progressStatus: statusRes?.data?.status, isLearning: true })
    dashboardStore.invalidateCache()
    ElMessage.success('已加入学习')
    await loadList()
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '加入学习失败')
  } finally {
    row.adding = false
  }
}

const retryPendingBatch = async () => {
  if (retryPendingLoading.value) return
  retryPendingLoading.value = true
  try {
    const res = await request.post('/word/llm/retry-pending', null, {
      params: {
        wordType: query.type,
        limit: 30
      }
    })
    const queued = Number(res?.data?.queued || 0)
    if (queued > 0) {
      ElMessage.success(`已加入 ${queued} 个重试任务`)
    } else {
      ElMessage.info('当前没有待重试单词')
    }
  } catch (error) {
    ElMessage.error(error?.businessMessage || error?.message || '批量重试失败')
  } finally {
    retryPendingLoading.value = false
  }
}

watch(keywordInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    query.keyword = value.trim()
    pagination.page = 1
    syncRouteFromState()
    loadList()
  }, 300)
})

onMounted(async () => {
  syncStateFromRoute()
  await syncRouteFromState()
  await loadList()
})

onUnmounted(() => {
  if (listController) {
    listController.abort()
    listController = null
  }
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})
</script>

<style scoped>
.word-list-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 14px;
}

.search-input {
  width: 300px;
}

.pos-select {
  width: 160px;
}

.reset-search-btn {
  color: var(--color-muted);
}

.word-table {
  border-radius: 12px;
  overflow: hidden;
}

.word-link {
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  color: var(--color-primary-strong);
  font-size: 15px;
  font-weight: 700;
}

.word-link:hover {
  text-decoration: underline;
}

.word-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-speak {
  border: 1px solid var(--color-border-soft);
  background: var(--color-surface);
  color: var(--color-warning);
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
  cursor: pointer;
  white-space: nowrap;
}

.quick-speak:hover {
  border-color: var(--color-warning);
}

.operation-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.retry-pending-btn {
  border-color: var(--color-primary-strong);
  color: var(--color-primary-strong);
}

.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

@media (max-width: 768px) {
  .search-input,
  .pos-select {
    width: 100%;
  }

  .pager-wrap {
    justify-content: center;
  }
}
</style>


