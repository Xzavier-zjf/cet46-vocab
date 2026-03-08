<template>
  <section class="admin-page">
    <section class="admin-hero">
      <div>
        <h1>词库运营仪表盘</h1>
        <p>统一管理导入、回滚与 AI 解释生成任务。</p>
      </div>
      <div class="hero-metrics">
        <article class="metric-item">
          <span>批次数</span>
          <strong>{{ batches.length }}</strong>
        </article>
        <article class="metric-item">
          <span>已回滚</span>
          <strong>{{ rolledBackCount }}</strong>
        </article>
        <article class="metric-item">
          <span>最近批次</span>
          <strong>{{ latestBatchId }}</strong>
        </article>
      </div>
    </section>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-title">导入文件</div>
      </template>
      <div class="row">
        <el-select v-model="wordType" class="field">
          <el-option label="CET4" value="cet4" />
          <el-option label="CET6" value="cet6" />
        </el-select>
        <el-upload :auto-upload="false" :show-file-list="true" :on-change="handleFileChange" :limit="1" accept=".csv,text/csv">
          <el-button>选择 CSV 文件</el-button>
        </el-upload>
        <el-button :loading="previewLoading" @click="submitPreview">导入预览</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">确认导入</el-button>
      </div>
      <p class="hint">建议先预览后导入。支持列：`id,english,sent,chinese` 或 `english,sent,chinese`。</p>
      <div v-if="previewResult" class="result">
        <p>预览结果：新增 {{ previewResult.inserted }}，更新 {{ previewResult.updated }}，跳过 {{ previewResult.skipped }}</p>
        <el-table v-if="Array.isArray(previewResult.samples) && previewResult.samples.length" :data="previewResult.samples" size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="english" label="单词" min-width="150" />
          <el-table-column prop="action" label="动作" width="110" />
          <el-table-column prop="chinese" label="中文释义" min-width="240" />
        </el-table>
        <p v-if="(previewResult.errors || []).length" class="error">错误：{{ previewResult.errors.slice(0, 5).join('；') }}</p>
      </div>
      <div v-if="importResult" class="result">
        <p>导入完成：批次 {{ importResult.batchId }}，新增 {{ importResult.inserted }}，更新 {{ importResult.updated }}，跳过 {{ importResult.skipped }}</p>
      </div>
    </el-card>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-title">导入批次回滚</div>
      </template>
      <div class="row">
        <el-button :loading="batchLoading" @click="loadBatches">刷新批次</el-button>
      </div>
      <el-table :data="batches" size="small">
        <el-table-column prop="batch_id" label="批次 ID" min-width="220" />
        <el-table-column prop="word_type" label="词库" width="90" />
        <el-table-column prop="inserted_count" label="新增" width="80" />
        <el-table-column prop="updated_count" label="更新" width="80" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              :disabled="row.status === 'ROLLED_BACK'"
              :loading="rollbackLoading && rollbackBatchId === row.batch_id"
              @click="rollbackBatch(row.batch_id)"
            >
              回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-title">智能解释生成</div>
      </template>
      <div class="row">
        <el-select v-model="style" class="field">
          <el-option label="故事风格" value="story" />
          <el-option label="学术风格" value="academic" />
          <el-option label="吐槽风格" value="sarcastic" />
        </el-select>
        <el-select v-model="provider" class="field">
          <el-option label="本地模型" value="local" />
          <el-option label="云端 API" value="cloud" />
        </el-select>
        <el-input-number v-model="limit" :min="1" :max="500" />
        <el-button type="primary" :loading="explainLoading" @click="generateExplainAll">批量生成解释</el-button>
        <el-button :loading="missingLoading" @click="generateExplainMissing">仅补齐缺失解释</el-button>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const wordType = ref('cet4')
const style = ref('story')
const provider = ref('local')
const limit = ref(100)
const selectedFile = ref(null)

const previewLoading = ref(false)
const importLoading = ref(false)
const previewResult = ref(null)
const importResult = ref(null)

const batches = ref([])
const batchLoading = ref(false)
const rollbackLoading = ref(false)
const rollbackBatchId = ref('')

const explainLoading = ref(false)
const missingLoading = ref(false)

const rolledBackCount = computed(() => batches.value.filter((b) => b?.status === 'ROLLED_BACK').length)
const latestBatchId = computed(() => {
  const first = batches.value[0]?.batch_id
  if (!first) return '-'
  return String(first).slice(0, 8)
})

const handleFileChange = (file) => {
  selectedFile.value = file?.raw || null
}

const buildFormData = () => {
  const formData = new FormData()
  formData.append('wordType', wordType.value)
  formData.append('file', selectedFile.value)
  return formData
}

const submitPreview = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  previewLoading.value = true
  try {
    const res = await request.post('/admin/word-bank/preview', buildFormData(), {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
    previewResult.value = res?.data || null
    ElMessage.success('预览完成')
  } finally {
    previewLoading.value = false
  }
}

const submitImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  importLoading.value = true
  try {
    const res = await request.post('/admin/word-bank/import', buildFormData(), {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
    importResult.value = res?.data || null
    ElMessage.success('导入完成')
    await loadBatches()
  } finally {
    importLoading.value = false
  }
}

const loadBatches = async () => {
  batchLoading.value = true
  try {
    const res = await request.get('/admin/word-bank/batches', { params: { wordType: wordType.value } })
    batches.value = Array.isArray(res?.data) ? res.data : []
  } finally {
    batchLoading.value = false
  }
}

const rollbackBatch = async (batchId) => {
  await ElMessageBox.confirm(`确认回滚批次 ${batchId} 吗？`, '回滚确认', { type: 'warning' })
  rollbackLoading.value = true
  rollbackBatchId.value = batchId
  try {
    const res = await request.post('/admin/word-bank/rollback', { batchId })
    ElMessage.success(`回滚完成，${res?.data?.rolledBack || 0} 条`)
    await loadBatches()
  } finally {
    rollbackLoading.value = false
    rollbackBatchId.value = ''
  }
}

const generateExplainAll = async () => {
  explainLoading.value = true
  try {
    const res = await request.post('/admin/word-bank/generate-explain', {
      wordType: wordType.value,
      style: style.value,
      provider: provider.value,
      limit: limit.value
    })
    ElMessage.success(`已提交 ${res?.data?.queued || 0} 个解释生成任务`)
  } finally {
    explainLoading.value = false
  }
}

const generateExplainMissing = async () => {
  missingLoading.value = true
  try {
    const res = await request.post('/admin/word-bank/generate-explain-missing', {
      wordType: wordType.value,
      style: style.value,
      provider: provider.value,
      limit: limit.value
    })
    ElMessage.success(`已提交 ${res?.data?.queued || 0} 个缺失解释补齐任务`)
  } finally {
    missingLoading.value = false
  }
}

onMounted(async () => {
  await loadBatches()
})
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-hero {
  background: linear-gradient(120deg, #1a2b4a 0%, #243f68 100%);
  border: 1px solid rgba(201, 168, 76, 0.45);
  border-radius: var(--radius-card);
  padding: 18px 20px;
  box-shadow: var(--shadow-card);
  color: #ebf1fb;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
}

.admin-hero h1 {
  margin: 0;
  color: #fff;
}

.admin-hero p {
  margin: 8px 0 0;
  color: #cfd9ea;
  font-size: 13px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-item {
  min-width: 90px;
  border: 1px solid rgba(201, 168, 76, 0.35);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 10px;
  text-align: center;
}

.metric-item span {
  font-size: 12px;
  color: #dce5f4;
}

.metric-item strong {
  display: block;
  margin-top: 4px;
  color: #fff4d2;
  font-size: 20px;
}

.panel-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.panel-title {
  font-weight: 700;
  color: #1a2b4a;
}

.row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.field {
  width: 160px;
}

.hint {
  margin: 10px 0 0;
  color: #64748b;
}

.result {
  margin-top: 12px;
}

.error {
  margin-top: 8px;
  color: #b91c1c;
}

@media (max-width: 900px) {
  .admin-hero {
    grid-template-columns: 1fr;
  }
}
</style>
