<template>
  <section class="word-list-page">
    <div class="filter-bar">
      <el-radio-group v-model="query.type" @change="onFilterChange">
        <el-radio-button label="cet4">CET4</el-radio-button>
        <el-radio-button label="cet6">CET6</el-radio-button>
      </el-radio-group>

      <el-input
        v-model="keywordInput"
        class="search-input"
        clearable
        placeholder="搜索英文单词"
        @clear="onKeywordClear"
      />

      <el-select v-model="query.pos" class="pos-select" placeholder="词性筛选" @change="onFilterChange">
        <el-option label="全部词性" value="" />
        <el-option label="v." value="v" />
        <el-option label="n." value="n" />
        <el-option label="adj." value="adj" />
        <el-option label="adv." value="adv" />
        <el-option label="其他" value="other" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="tableData" class="word-table" border>
      <el-table-column label="英文单词" min-width="200">
        <template #default="scope">
          <button class="word-link" @click="goDetail(scope.row)">{{ scope.row.english }}</button>
        </template>
      </el-table-column>
      <el-table-column label="音标" prop="phonetic" min-width="160" />
      <el-table-column label="中文释义" prop="chinese" min-width="320" show-overflow-tooltip />
      <el-table-column label="词性" prop="pos" width="110" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="scope">
          <span v-if="scope.row.isLearning" class="learning-tag">学习中</span>
          <el-button
            v-else
            class="add-btn"
            size="small"
            :loading="scope.row.adding"
            @click="addToLearn(scope.row)"
          >
            加入学习
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-wrap">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="pagination.total"
        :current-page="pagination.page"
        :page-size="pagination.size"
        @current-change="onPageChange"
      />
    </div>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])

const query = reactive({
  type: 'cet4',
  keyword: '',
  pos: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const keywordInput = ref('')
let searchTimer = null
let listController = null

const normalizePos = (value) => {
  if (!value) return ''
  if (value === 'other') return ''
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
    const params = {
      type: query.type,
      page: pagination.page,
      size: pagination.size,
      keyword: query.keyword || undefined,
      pos: normalizePos(query.pos) || undefined
    }
    const res = await request.get('/word/list', {
      params,
      signal: listController.signal
    })
    const list = Array.isArray(res?.data?.list) ? res.data.list : []
    const mapped = list.map((item) => ({ ...item, adding: false }))

    if (query.pos === 'other') {
      tableData.value = mapped.filter((item) => isOtherPos(item.pos))
    } else {
      tableData.value = mapped
    }

    pagination.total = Number(res?.data?.total || 0)
    pagination.page = Number(res?.data?.page || pagination.page)
    pagination.size = Number(res?.data?.size || pagination.size)
  } catch (error) {
    if (error?.code !== 'ERR_CANCELED') {
      throw error
    }
  } finally {
    if (listController?.signal?.aborted) {
      return
    }
    loading.value = false
  }
}

const onFilterChange = () => {
  pagination.page = 1
  loadList()
}

const onPageChange = (page) => {
  pagination.page = page
  loadList()
}

const onKeywordClear = () => {
  query.keyword = ''
  pagination.page = 1
  loadList()
}

const goDetail = (row) => {
  router.push(`/words/${row.wordType}/${row.wordId}`)
}

const addToLearn = async (row) => {
  if (row.isLearning || row.adding) return
  row.adding = true
  try {
    await request.post('/word/learn/add', {
      wordId: row.wordId,
      wordType: row.wordType
    })
    row.isLearning = true
    ElMessage.success('已加入学习计划')
  } finally {
    row.adding = false
  }
}

watch(keywordInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    query.keyword = value.trim()
    pagination.page = 1
    loadList()
  }, 300)
})

onMounted(() => {
  loadList()
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
  background: #fff;
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

.word-table {
  border-radius: 12px;
  overflow: hidden;
}

.word-link {
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  color: #1A2B4A;
  font-size: 15px;
  font-weight: 700;
}

.word-link:hover {
  text-decoration: underline;
}

.learning-tag {
  color: #8BAFD4;
  font-weight: 600;
}

.add-btn {
  border: 1px solid #C9A84C;
  color: #C9A84C;
  background: #fff;
}

.add-btn:hover {
  border-color: #b79434;
  color: #b79434;
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
