<template>
  <section class="admin-page">
    <h1>词库导入与回滚</h1>

    <el-card class="card" shadow="never">
      <template #header>导入文件</template>
      <div class="row">
        <el-select v-model="wordType" class="field">
          <el-option label="CET4" value="cet4" />
          <el-option label="CET6" value="cet6" />
        </el-select>
        <el-upload :auto-upload="false" :show-file-list="true" :on-change="handleFileChange" :limit="1" accept=".csv,text/csv">
          <el-button>选择CSV文件</el-button>
        </el-upload>
        <el-button :loading="previewLoading" @click="submitPreview">导入预览</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">确认导入</el-button>
      </div>
      <p class="hint">先点“导入预览”校验，再点“确认导入”。支持 `id,english,sent,chinese` 或 `english,sent,chinese`。</p>
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

    <el-card class="card" shadow="never">
      <template #header>导入批次回滚</template>
      <div class="row">
        <el-button :loading="batchLoading" @click="loadBatches">刷新批次</el-button>
      </div>
      <el-table :data="batches" size="small">
        <el-table-column prop="batch_id" label="批次ID" min-width="220" />
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

    <el-card class="card" shadow="never">
      <template #header>智能解释生成</template>
      <div class="row">
        <el-select v-model="style" class="field">
          <el-option label="故事风格" value="story" />
          <el-option label="学术风格" value="academic" />
          <el-option label="吐槽风格" value="sarcastic" />
        </el-select>
        <el-select v-model="provider" class="field">
          <el-option label="本地模型" value="local" />
          <el-option label="云端API" value="cloud" />
        </el-select>
        <el-input-number v-model="limit" :min="1" :max="500" />
        <el-button type="primary" :loading="explainLoading" @click="generateExplainAll">批量生成解释</el-button>
        <el-button :loading="missingLoading" @click="generateExplainMissing">仅补齐缺失解释</el-button>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
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
    ElMessage.warning('请先选择CSV文件')
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
    ElMessage.warning('请先选择CSV文件')
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
    ElMessage.success(`回滚完成：${res?.data?.rolledBack || 0} 条`)
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
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  border-radius: 12px;
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
</style>

