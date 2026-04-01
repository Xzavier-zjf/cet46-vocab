<template>
  <section class="profile-page">
    <section class="card">
      <h2>{{ TEXT.pageTitle }}</h2>

      <div class="row">
        <span class="label">{{ TEXT.username }}</span>
        <el-input :model-value="form.username" readonly disabled />
      </div>

      <div class="row">
        <span class="label">{{ TEXT.avatar }}</span>
        <div class="avatar-row">
          <el-avatar :size="72" :src="avatarPreview || userStore.avatar || undefined">
            {{ avatarText }}
          </el-avatar>
          <input class="avatar-input" type="file" accept="image/*" @change="handleAvatarChange" />
        </div>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.nickname }}</span>
        <el-input v-model="form.nickname" maxlength="50" :placeholder="TEXT.nicknamePlaceholder" />
      </div>

      <div class="row">
        <span class="label">{{ TEXT.style }}</span>
        <div class="style-grid">
          <button
            v-for="item in styles"
            :key="item.value"
            class="style-card"
            :class="{ active: form.llmStyle === item.value }"
            @click="form.llmStyle = item.value"
          >
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.provider }}</span>
        <div class="provider-grid">
          <button
            v-for="item in providers"
            :key="item.value"
            class="style-card"
            :class="{ active: form.llmProvider === item.value }"
            @click="form.llmProvider = item.value"
          >
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
          </button>
        </div>
        <div class="model-overview">
          <div class="model-card">
            <div class="model-card-head">
              <span class="model-card-title">{{ TEXT.currentModelCard }}</span>
              <el-tag size="small" effect="plain">{{ currentProviderLabel }}</el-tag>
            </div>
            <div class="model-card-model">{{ currentModelLabel }}</div>
          </div>
          <div class="model-card">
            <div class="model-card-head">
              <span class="model-card-title">{{ TEXT.lastUsedModelCard }}</span>
              <el-tag size="small" effect="plain">{{ lastUsedProviderLabel }}</el-tag>
            </div>
            <div class="model-card-model">{{ lastUsedModelLabel }}</div>
            <div class="model-card-meta">{{ TEXT.requestScene }}{{ lastUsedSourceLabel }}</div>
            <div class="model-card-meta">{{ TEXT.requestTime }}{{ lastUsedTimeLabel }}</div>
            <div class="model-card-state" :class="{ mismatch: !lastUsedMatchesCurrent }">
              {{ lastUsedMatchLabel }}
            </div>
          </div>
        </div>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.localModel }}</span>
        <div class="health-actions">
          <el-select
            v-model="form.llmLocalModel"
            filterable
            clearable
            :placeholder="TEXT.localModelPlaceholder"
            style="min-width: 320px"
            :loading="localModelsLoading"
            :disabled="localModelsLoading || localModels.length === 0"
          >
            <el-option
              v-for="item in localModels"
              :key="item.name"
              :label="item.name"
              :value="item.name"
            />
          </el-select>
          <BtnSecondary class="health-btn" :loading="localModelsLoading" @click="loadLocalModels">{{ TEXT.refreshModels }}</BtnSecondary>
          <span class="health-summary">{{ TEXT.availableCount }}{{ localModels.length }}</span>
        </div>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.cloudModel }}</span>
        <div class="health-actions">
          <el-select
            v-model="form.llmCloudModel"
            filterable
            clearable
            :placeholder="TEXT.cloudModelPlaceholder"
            style="min-width: 320px"
            :loading="cloudModelsLoading"
            :disabled="cloudModelsLoading || cloudModels.length === 0"
          >
            <el-option
              v-for="item in cloudModels"
              :key="item.name"
              :label="cloudModelOptionLabel(item)"
              :value="item.name"
            />
          </el-select>
          <BtnSecondary class="health-btn" :loading="cloudModelsLoading" @click="loadCloudModels">{{ TEXT.refreshModels }}</BtnSecondary>
          <span class="health-summary">{{ TEXT.availableCount }}{{ cloudModels.length }}</span>
        </div>
      </div>

      <div v-if="isAdmin" class="row">
        <span class="label">{{ TEXT.cloudModelManage }}</span>
        <div class="health-actions">
          <BtnPrimary class="health-btn" @click="openCloudModelCreate">{{ TEXT.cloudModelAdd }}</BtnPrimary>
          <BtnSecondary class="health-btn" :loading="adminCloudModelsLoading" @click="loadAdminCloudModels">{{ TEXT.refreshModels }}</BtnSecondary>
        </div>
        <el-table :data="adminCloudModels" size="small" class="admin-model-table">
          <el-table-column :label="TEXT.cloudModelProvider" width="120">
            <template #default="{ row }">{{ cloudProviderLabel(row.provider) }}</template>
          </el-table-column>
          <el-table-column prop="displayName" :label="TEXT.cloudModelName" min-width="160" />
          <el-table-column prop="modelKey" :label="TEXT.cloudModelKey" min-width="220" />
          <el-table-column :label="TEXT.cloudModelStatus" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? TEXT.cloudModelEnabled : TEXT.cloudModelDisabled }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="TEXT.cloudModelDefault" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" size="small" effect="plain" type="warning">{{ TEXT.yes }}</el-tag>
              <span v-else>{{ TEXT.no }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="TEXT.actions" width="280">
            <template #default="{ row }">
              <el-button size="small" @click="openCloudModelEdit(row)">{{ TEXT.edit }}</el-button>
              <el-button size="small" :disabled="row.isDefault || !row.enabled" @click="setCloudModelDefault(row)">{{ TEXT.setDefault }}</el-button>
              <el-button size="small" type="danger" :disabled="row.isDefault" @click="removeCloudModel(row)">{{ TEXT.delete }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="!isAdmin" class="row">
        <span class="label">{{ TEXT.privateCloudModelManage }}</span>
        <div class="health-actions">
          <BtnPrimary class="health-btn" @click="openPrivateCloudModelCreate">{{ TEXT.privateCloudModelAdd }}</BtnPrimary>
          <BtnSecondary class="health-btn" :loading="privateCloudModelsLoading" @click="loadPrivateCloudModels">{{ TEXT.refreshModels }}</BtnSecondary>
        </div>
        <el-table :data="privateCloudModels" size="small" class="admin-model-table">
          <el-table-column :label="TEXT.cloudModelProvider" width="120">
            <template #default="{ row }">{{ cloudProviderLabel(row.provider) }}</template>
          </el-table-column>
          <el-table-column prop="displayName" :label="TEXT.cloudModelName" min-width="160" />
          <el-table-column prop="modelKey" :label="TEXT.cloudModelKey" min-width="220" />
          <el-table-column :label="TEXT.cloudModelStatus" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? TEXT.cloudModelEnabled : TEXT.cloudModelDisabled }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="TEXT.actions" width="200">
            <template #default="{ row }">
              <el-button size="small" @click="openPrivateCloudModelEdit(row)">{{ TEXT.edit }}</el-button>
              <el-button size="small" type="danger" @click="removePrivateCloudModel(row)">{{ TEXT.delete }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.localHealth }}</span>
        <div class="health-actions">
          <BtnSecondary class="health-btn" :loading="localHealthChecking" @click="checkLocalHealth">{{ TEXT.checkLocal }}</BtnSecondary>
          <span v-if="localHealthResult" class="health-summary">
            {{ localHealthResult.message }}
            <template v-if="localHealthResult.latencyMs">({{ localHealthResult.latencyMs }}ms)</template>
          </span>
        </div>
      </div>

      <div class="row">
        <span class="label">{{ TEXT.cloudHealth }}</span>
        <div class="health-actions">
          <BtnSecondary class="health-btn" :loading="cloudHealthChecking" @click="checkCloudHealth">{{ TEXT.checkCloud }}</BtnSecondary>
          <span v-if="cloudHealthResult" class="health-summary">
            {{ cloudHealthResult.message }}
            <template v-if="cloudHealthResult.latencyMs">({{ cloudHealthResult.latencyMs }}ms)</template>
          </span>
        </div>
      </div>

      <div class="row" v-if="!isAdmin">
        <span class="label">{{ TEXT.dailyTarget }}</span>
        <el-input-number v-model="form.dailyTarget" :min="1" :max="100" />
      </div>

      <div class="security-panel">
        <div class="security-text">
          <h3>{{ TEXT.accountSecurity }}</h3>
          <p>{{ TEXT.accountSecurityDesc }}</p>
        </div>
        <BtnSecondary class="health-btn" @click="openPasswordDialog">{{ TEXT.changePassword }}</BtnSecondary>
      </div>

      <BtnPrimary class="save-btn" :loading="saving" @click="saveAll">{{ TEXT.save }}</BtnPrimary>
    </section>

    <el-dialog
      v-model="passwordDialogVisible"
      :title="TEXT.changePassword"
      width="420px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item :label="TEXT.oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password :placeholder="TEXT.oldPasswordPlaceholder" />
        </el-form-item>
        <el-form-item :label="TEXT.newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password :placeholder="TEXT.newPasswordPlaceholder" />
        </el-form-item>
        <el-form-item :label="TEXT.confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password :placeholder="TEXT.confirmPasswordPlaceholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <BtnSecondary @click="passwordDialogVisible = false">{{ TEXT.cancel }}</BtnSecondary>
        <BtnPrimary :loading="pwdSaving" @click="changePassword">{{ TEXT.confirm }}</BtnPrimary>
      </template>
    </el-dialog>

    <el-dialog
      v-model="cloudModelDialogVisible"
      :title="cloudModelDialogTitle"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item :label="TEXT.cloudModelProvider">
          <el-select
            v-model="cloudModelForm.provider"
            :placeholder="TEXT.cloudModelProviderPlaceholder"
            :disabled="!isAdmin || cloudModelManageScope !== 'admin'"
            style="min-width: 220px"
          >
            <el-option
              v-for="item in cloudModelProviderOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="TEXT.cloudModelName">
          <el-input v-model="cloudModelForm.displayName" maxlength="128" :placeholder="TEXT.cloudModelNamePlaceholder" />
        </el-form-item>
        <el-form-item :label="TEXT.cloudModelKey">
          <el-input v-model="cloudModelForm.modelKey" maxlength="128" :placeholder="TEXT.cloudModelKeyPlaceholder" />
        </el-form-item>
        <el-form-item :label="TEXT.cloudModelStatus">
          <el-switch v-model="cloudModelForm.enabled" />
        </el-form-item>
        <el-form-item v-if="isAdmin && cloudModelManageScope === 'admin'" :label="TEXT.cloudModelDefault">
          <el-switch v-model="cloudModelForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <BtnSecondary @click="cloudModelDialogVisible = false">{{ TEXT.cancel }}</BtnSecondary>
        <BtnPrimary :loading="cloudModelSaving" @click="saveCloudModel">{{ TEXT.confirm }}</BtnPrimary>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import BtnSecondary from '@/components/common/BtnSecondary.vue'

const TEXT = {
  pageTitle: '\u6211\u7684\u8d44\u6599',
  username: '\u7528\u6237\u540d',
  avatar: '\u5934\u50cf',
  nickname: '\u6635\u79f0',
  nicknamePlaceholder: '\u8bf7\u8f93\u5165\u6635\u79f0',
  style: '\u5b66\u4e60\u98ce\u683c',
  provider: '\u6a21\u578b\u6765\u6e90',
  localModel: '\u672c\u5730\u6a21\u578b',
  localModelPlaceholder: '\u9009\u62e9\u672c\u5730\u6a21\u578b',
  cloudModel: '\u4e91\u7aef\u6a21\u578b',
  cloudModelPlaceholder: '\u9009\u62e9\u4e91\u7aef\u6a21\u578b',
  refreshModels: '\u5237\u65b0\u6a21\u578b\u5217\u8868',
  availableCount: '\u53ef\u7528\u6570\u91cf\uff1a',
  currentModelCard: '\u5f53\u524d\u914d\u7f6e\u6a21\u578b',
  cloudModelUnknown: '\u4e91\u7aef\u6a21\u578b\u672a\u914d\u7f6e',
  lastUsedModelCard: '\u6700\u8fd1\u4e00\u6b21\u5b9e\u9645\u8bf7\u6c42',
  noLastUsedModel: '\u6682\u65e0\u8bb0\u5f55',
  noModel: '\u672a\u9009\u62e9',
  requestScene: '\u8bf7\u6c42\u573a\u666f\uff1a',
  requestTime: '\u8bf7\u6c42\u65f6\u95f4\uff1a',
  sourceUnknown: '\u672a\u8bb0\u5f55',
  timeUnknown: '\u6682\u65e0',
  providerLocal: '\u672c\u5730\u6a21\u578b',
  providerCloud: '\u4e91\u7aef API',
  providerUnknown: '\u672a\u77e5\u63d0\u4f9b\u65b9',
  modelMatched: '\u4e0e\u5f53\u524d\u914d\u7f6e\u4e00\u81f4',
  modelMismatched: '\u4e0e\u5f53\u524d\u914d\u7f6e\u4e0d\u4e00\u81f4',
  localHealth: '\u672c\u5730\u8fde\u901a\u81ea\u68c0',
  checkLocal: '\u68c0\u6d4b\u672c\u5730\u6a21\u578b',
  cloudHealth: '\u4e91\u7aef\u8fde\u901a\u81ea\u68c0',
  checkCloud: '\u68c0\u6d4b\u4e91\u7aef API',
  dailyTarget: '\u6bcf\u65e5\u76ee\u6807',
  accountSecurity: '\u8d26\u53f7\u5b89\u5168',
  accountSecurityDesc: '\u5efa\u8bae\u5b9a\u671f\u66f4\u65b0\u5bc6\u7801\uff0c\u63d0\u9ad8\u8d26\u53f7\u5b89\u5168\u6027\u3002',
  changePassword: '\u4fee\u6539\u5bc6\u7801',
  save: '\u4fdd\u5b58\u8bbe\u7f6e',
  oldPassword: '\u65e7\u5bc6\u7801',
  oldPasswordPlaceholder: '\u8bf7\u8f93\u5165\u65e7\u5bc6\u7801',
  newPassword: '\u65b0\u5bc6\u7801',
  newPasswordPlaceholder: '\u8bf7\u8f93\u5165\u65b0\u5bc6\u7801\uff086-20\u4f4d\uff09',
  confirmPassword: '\u786e\u8ba4\u65b0\u5bc6\u7801',
  confirmPasswordPlaceholder: '\u8bf7\u518d\u6b21\u8f93\u5165\u65b0\u5bc6\u7801',
  cancel: '\u53d6\u6d88',
  confirm: '\u786e\u8ba4',
  pickImage: '\u8bf7\u9009\u62e9\u56fe\u7247\u6587\u4ef6',
  avatarLimit: '\u5934\u50cf\u5927\u5c0f\u4e0d\u80fd\u8d85\u8fc7 2MB',
  nicknameRequired: '\u6635\u79f0\u4e0d\u80fd\u4e3a\u7a7a',
  profileSaved: '\u8d44\u6599\u5df2\u66f4\u65b0',
  passwordRequired: '\u8bf7\u5b8c\u6574\u586b\u5199\u5bc6\u7801\u4fe1\u606f',
  passwordLength: '\u65b0\u5bc6\u7801\u957f\u5ea6\u9700\u4e3a 6-20 \u4f4d',
  passwordMismatch: '\u4e24\u6b21\u8f93\u5165\u7684\u65b0\u5bc6\u7801\u4e0d\u4e00\u81f4',
  passwordChanged: '\u5bc6\u7801\u4fee\u6539\u6210\u529f',
  styleAcademicTitle: '\u5b66\u672f\u98ce\u683c',
  styleAcademicDesc: '\u504f\u89c4\u5219\u4e0e\u8bcd\u6e90\u89e3\u91ca',
  styleStoryTitle: '\u6545\u4e8b\u98ce\u683c',
  styleStoryDesc: '\u504f\u573a\u666f\u8054\u60f3\u8bb0\u5fc6',
  styleSarcasticTitle: '\u5410\u69fd\u98ce\u683c',
  styleSarcasticDesc: '\u9ad8\u53cd\u5dee\u8bb0\u5fc6\u70b9',
  providerLocalTitle: '\u672c\u5730\u6a21\u578b',
  providerLocalDesc: 'Ollama \u672c\u5730\u63a8\u7406',
  providerCloudTitle: '\u4e91\u7aef API',
  providerCloudDesc: '\u4e91\u6a21\u578b\u6548\u679c\u66f4\u7a33\u5b9a',
  cloudModelManage: '\u4e91\u7aef\u6a21\u578b\u7ba1\u7406',
  cloudModelAdd: '\u65b0\u589e\u6a21\u578b',
  cloudModelProvider: '\u4f9b\u5e94\u5546',
  cloudModelProviderPlaceholder: '\u9009\u62e9\u4f9b\u5e94\u5546\uff08\u7ba1\u7406\u5458\u53ef\u7f16\u8f91\uff09',
  cloudModelName: '\u663e\u793a\u540d\u79f0\uff08\u53ef\u9009\uff09',
  cloudModelNamePlaceholder: '\u7528\u4e8e\u754c\u9762\u663e\u793a\uff08\u53ef\u9009\uff09',
  cloudModelKey: 'Model ID',
  cloudModelKeyPlaceholder: '\u4f8b\u5982 qwen3.5-flash\uff08\u5fc5\u586b\uff0c\u4e0d\u662f API Key\uff09',
  cloudModelStatus: '\u542f\u7528\u72b6\u6001',
  cloudModelEnabled: '\u5df2\u542f\u7528',
  cloudModelDisabled: '\u5df2\u505c\u7528',
  cloudModelDefault: '\u9ed8\u8ba4',
  setDefault: '\u8bbe\u4e3a\u9ed8\u8ba4',
  edit: '\u7f16\u8f91',
  delete: '\u5220\u9664',
  actions: '\u64cd\u4f5c',
  yes: '\u662f',
  no: '\u5426',
  cloudModelCreateTitle: '\u65b0\u589e\u4e91\u7aef\u6a21\u578b',
  cloudModelEditTitle: '\u7f16\u8f91\u4e91\u7aef\u6a21\u578b',
  cloudModelSaved: '\u6a21\u578b\u4fdd\u5b58\u6210\u529f',
  cloudModelDeleted: '\u6a21\u578b\u5220\u9664\u6210\u529f',
  cloudModelSetDefaultSuccess: '\u5df2\u8bbe\u4e3a\u9ed8\u8ba4\u6a21\u578b',
  cloudModelRequired: '\u8bf7\u586b\u5199 Model ID',
  cloudModelDeleteConfirm: '\u786e\u8ba4\u5220\u9664\u8be5\u6a21\u578b\u5417\uff1f',
  privateCloudModelManage: '\u6211\u7684\u79c1\u6709\u4e91\u7aef\u6a21\u578b',
  privateCloudModelAdd: '\u65b0\u589e\u79c1\u6709\u6a21\u578b',
  privateCloudModelCreateTitle: '\u65b0\u589e\u79c1\u6709\u4e91\u7aef\u6a21\u578b',
  privateCloudModelEditTitle: '\u7f16\u8f91\u79c1\u6709\u4e91\u7aef\u6a21\u578b'
}

const userStore = useUserStore()
const saving = ref(false)
const pwdSaving = ref(false)
const passwordDialogVisible = ref(false)
const localHealthChecking = ref(false)
const localHealthResult = ref(null)
const cloudHealthChecking = ref(false)
const cloudHealthResult = ref(null)
const cloudModelName = ref('')
const lastUsed = ref(null)
const localModelsLoading = ref(false)
const localModels = ref([])
const cloudModelsLoading = ref(false)
const cloudModels = ref([])
const adminCloudModelsLoading = ref(false)
const adminCloudModels = ref([])
const privateCloudModelsLoading = ref(false)
const privateCloudModels = ref([])
const cloudModelDialogVisible = ref(false)
const cloudModelSaving = ref(false)
const cloudModelEditingId = ref(null)
const cloudModelManageScope = ref('admin')
const cloudModelOriginalEnabled = ref(null)
const avatarFile = ref(null)
const avatarPreview = ref('')
const objectUrl = ref('')

const styles = [
  { value: 'academic', title: TEXT.styleAcademicTitle, desc: TEXT.styleAcademicDesc },
  { value: 'story', title: TEXT.styleStoryTitle, desc: TEXT.styleStoryDesc },
  { value: 'sarcastic', title: TEXT.styleSarcasticTitle, desc: TEXT.styleSarcasticDesc }
]

const providers = [
  { value: 'local', title: TEXT.providerLocalTitle, desc: TEXT.providerLocalDesc },
  { value: 'cloud', title: TEXT.providerCloudTitle, desc: TEXT.providerCloudDesc }
]

const DEFAULT_CLOUD_PROVIDER_LABELS = {
  bailian: '\u767e\u70bc',
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  openrouter: 'OpenRouter',
  groq: 'Groq',
  xai: 'xAI',
  minimax: 'MiniMax'
}

const cloudProviderLabels = ref({ ...DEFAULT_CLOUD_PROVIDER_LABELS })
const cloudModelProviderOptions = ref([
  { value: 'bailian', label: '\u767e\u70bc' }
])

const form = reactive({
  username: '',
  nickname: '',
  llmStyle: 'story',
  llmProvider: 'local',
  llmLocalModel: '',
  llmCloudModel: '',
  dailyTarget: 20
})
const cloudModelForm = reactive({
  provider: 'bailian',
  modelKey: '',
  displayName: '',
  enabled: true,
  isDefault: false
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarText = computed(() => (form.nickname || form.username || userStore.nickname || userStore.username || TEXT.pageTitle).slice(0, 1))
const isAdmin = computed(() => userStore.role === 'ADMIN')
const currentModelLabel = computed(() => {
  if (form.llmProvider === 'cloud') {
    return form.llmCloudModel || cloudModelName.value || cloudHealthResult.value?.model || TEXT.cloudModelUnknown
  }
  return form.llmLocalModel || TEXT.noModel
})
const currentProviderLabel = computed(() => providerLabel(form.llmProvider))
const lastUsedModelLabel = computed(() => {
  const data = lastUsed.value
  if (!data || !data.model) {
    return TEXT.noLastUsedModel
  }
  return data.model
})
const lastUsedProviderLabel = computed(() => providerLabel(lastUsed.value?.provider))
const lastUsedSourceLabel = computed(() => {
  const source = String(lastUsed.value?.source || '').trim()
  return source || TEXT.sourceUnknown
})
const lastUsedTimeLabel = computed(() => formatTimestamp(lastUsed.value?.updatedAt))
const lastUsedMatchesCurrent = computed(() => {
  const data = lastUsed.value
  if (!data || !data.model) {
    return true
  }
  const providerMatched = String(data.provider || '').trim() === String(form.llmProvider || '').trim()
  const modelMatched = String(data.model || '').trim() === String(currentModelLabel.value || '').trim()
  return providerMatched && modelMatched
})
const lastUsedMatchLabel = computed(() => (lastUsedMatchesCurrent.value ? TEXT.modelMatched : TEXT.modelMismatched))
const cloudModelDialogTitle = computed(() => {
  if (cloudModelManageScope.value === 'private') {
    return cloudModelEditingId.value ? TEXT.privateCloudModelEditTitle : TEXT.privateCloudModelCreateTitle
  }
  return cloudModelEditingId.value ? TEXT.cloudModelEditTitle : TEXT.cloudModelCreateTitle
})

const syncFromStore = () => {
  form.username = userStore.username || ''
  form.nickname = userStore.nickname || ''
  form.llmStyle = userStore.llmStyle || 'story'
  form.llmProvider = userStore.llmProvider || 'local'
  form.llmLocalModel = userStore.llmLocalModel || ''
  form.llmCloudModel = userStore.llmCloudModel || ''
  form.dailyTarget = userStore.dailyTarget || 20
}

const revokePreview = () => {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

const resetPasswordForm = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
}

const openPasswordDialog = () => {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

const handleAvatarChange = (event) => {
  const file = event.target?.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(TEXT.pickImage)
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning(TEXT.avatarLimit)
    return
  }
  avatarFile.value = file
  revokePreview()
  objectUrl.value = URL.createObjectURL(file)
  avatarPreview.value = objectUrl.value
}

const loadLocalModels = async () => {
  localModelsLoading.value = true
  try {
    const res = await request.get('/user/llm/local-models', { timeout: 30000 })
    const data = res?.data || {}
    localModels.value = Array.isArray(data.models) ? data.models : []
    if (!form.llmLocalModel && data.selectedModel) {
      form.llmLocalModel = data.selectedModel
    }
    if (form.llmLocalModel && !localModels.value.some((item) => item?.name === form.llmLocalModel)) {
      form.llmLocalModel = data.selectedModel || ''
    }
  } finally {
    localModelsLoading.value = false
  }
}


const loadCloudModels = async () => {
  cloudModelsLoading.value = true
  try {
    const res = await request.get('/user/llm/cloud-models', { timeout: 30000 })
    const data = res?.data || {}
    cloudModels.value = Array.isArray(data.models) ? data.models : []
    const providers = Array.isArray(data.providers) ? data.providers : []
    const providerLabelsRaw = data.providerLabels && typeof data.providerLabels === 'object' ? data.providerLabels : {}
    const normalizedLabels = { ...DEFAULT_CLOUD_PROVIDER_LABELS }
    Object.entries(providerLabelsRaw).forEach(([key, label]) => {
      const normalizedKey = String(key || '').trim().toLowerCase()
      const normalizedLabel = String(label || '').trim()
      if (normalizedKey && normalizedLabel) {
        normalizedLabels[normalizedKey] = normalizedLabel
      }
    })
    cloudProviderLabels.value = normalizedLabels
    if (providers.length > 0) {
      cloudModelProviderOptions.value = providers.map((item) => {
        const value = String(item || '').trim().toLowerCase()
        return {
          value,
          label: cloudProviderLabel(value)
        }
      }).filter((item) => !!item.value)
    }
    if (!form.llmCloudModel && data.selectedModel) {
      form.llmCloudModel = data.selectedModel
    }
    if (form.llmCloudModel && !cloudModels.value.some((item) => item?.name === form.llmCloudModel)) {
      form.llmCloudModel = data.selectedModel || ''
    }
  } finally {
    cloudModelsLoading.value = false
  }
}
const saveAll = async () => {
  const nickname = form.nickname.trim()
  if (!nickname) {
    ElMessage.warning(TEXT.nicknameRequired)
    return
  }
  saving.value = true
  try {
    const profileForm = new FormData()
    profileForm.append('nickname', nickname)
    if (avatarFile.value) {
      profileForm.append('avatar', avatarFile.value)
    }
    await request.post('/user/profile', profileForm)

    const preferencePayload = {
      llmStyle: form.llmStyle,
      llmProvider: form.llmProvider,
      llmLocalModel: form.llmLocalModel || null,
      llmCloudModel: form.llmCloudModel || null,
      dailyTarget: form.dailyTarget
    }
    await request.put('/user/preference', preferencePayload)

    await userStore.fetchUserInfo()
    syncFromStore()
    await loadLocalModels()
    await loadCloudModels()
    await loadLastUsedModel()
    avatarFile.value = null
    avatarPreview.value = ''
    revokePreview()
    ElMessage.success(TEXT.profileSaved)
  } finally {
    saving.value = false
  }
}

const cloudModelOptionLabel = (item) => {
  if (!item) return ''
  const key = item.name || ''
  const displayName = item.displayName || ''
  return displayName && displayName !== key ? `${displayName} (${key})` : key
}

const loadAdminCloudModels = async () => {
  if (!isAdmin.value) return
  adminCloudModelsLoading.value = true
  try {
    const res = await request.get('/admin/llm/cloud-models', { timeout: 30000 })
    adminCloudModels.value = Array.isArray(res?.data) ? res.data : []
  } finally {
    adminCloudModelsLoading.value = false
  }
}

const loadPrivateCloudModels = async () => {
  if (isAdmin.value) return
  privateCloudModelsLoading.value = true
  try {
    const res = await request.get('/user/llm/cloud-models/private', { timeout: 30000 })
    privateCloudModels.value = Array.isArray(res?.data) ? res.data : []
  } finally {
    privateCloudModelsLoading.value = false
  }
}

const openCloudModelCreate = () => {
  cloudModelManageScope.value = isAdmin.value ? 'admin' : 'private'
  cloudModelEditingId.value = null
  cloudModelForm.provider = 'bailian'
  cloudModelForm.modelKey = ''
  cloudModelForm.displayName = ''
  cloudModelForm.enabled = true
  cloudModelForm.isDefault = false
  cloudModelOriginalEnabled.value = null
  cloudModelDialogVisible.value = true
}

const openPrivateCloudModelCreate = () => {
  cloudModelManageScope.value = 'private'
  cloudModelEditingId.value = null
  cloudModelForm.provider = 'bailian'
  cloudModelForm.modelKey = ''
  cloudModelForm.displayName = ''
  cloudModelForm.enabled = true
  cloudModelForm.isDefault = false
  cloudModelOriginalEnabled.value = null
  cloudModelDialogVisible.value = true
}

const openCloudModelEdit = (row) => {
  if (!row) return
  cloudModelManageScope.value = isAdmin.value ? 'admin' : 'private'
  cloudModelEditingId.value = row.id
  cloudModelForm.provider = row.provider || 'bailian'
  cloudModelForm.modelKey = row.modelKey || ''
  cloudModelForm.displayName = row.displayName || ''
  cloudModelForm.enabled = !!row.enabled
  cloudModelForm.isDefault = !!row.isDefault
  cloudModelOriginalEnabled.value = !!row.enabled
  cloudModelDialogVisible.value = true
}

const openPrivateCloudModelEdit = (row) => {
  if (!row) return
  cloudModelManageScope.value = 'private'
  cloudModelEditingId.value = row.id
  cloudModelForm.provider = row.provider || 'bailian'
  cloudModelForm.modelKey = row.modelKey || ''
  cloudModelForm.displayName = row.displayName || ''
  cloudModelForm.enabled = !!row.enabled
  cloudModelForm.isDefault = false
  cloudModelOriginalEnabled.value = !!row.enabled
  cloudModelDialogVisible.value = true
}

const saveCloudModel = async () => {
  const modelKey = String(cloudModelForm.modelKey || '').trim()
  if (!modelKey) {
    ElMessage.warning(TEXT.cloudModelRequired)
    return
  }
  cloudModelSaving.value = true
  try {
    const privatePayload = {
      modelKey,
      displayName: String(cloudModelForm.displayName || '').trim() || modelKey
    }
    const enabledFlag = !!cloudModelForm.enabled

    if (cloudModelManageScope.value === 'private') {
      if (cloudModelEditingId.value) {
        const fullPayload = {
          ...privatePayload,
          enabled: cloudModelOriginalEnabled.value !== null && enabledFlag !== !!cloudModelOriginalEnabled.value
            ? enabledFlag
            : null
        }
        await request.put(`/user/llm/cloud-models/private/${cloudModelEditingId.value}/full`, fullPayload)
      } else {
        await request.post('/user/llm/cloud-models/private', {
          ...privatePayload,
          enabled: enabledFlag
        })
      }
      await loadPrivateCloudModels()
    } else {
      const adminPayload = {
        ...privatePayload,
        enabled: enabledFlag,
        provider: String(cloudModelForm.provider || '').trim() || 'bailian',
        isDefault: !!cloudModelForm.isDefault
      }
      if (cloudModelEditingId.value) {
        await request.put(`/admin/llm/cloud-models/${cloudModelEditingId.value}`, adminPayload)
      } else {
        await request.post('/admin/llm/cloud-models', adminPayload)
      }
      await loadAdminCloudModels()
    }
    cloudModelDialogVisible.value = false
    await loadCloudModels()
    ElMessage.success(TEXT.cloudModelSaved)
  } finally {
    cloudModelSaving.value = false
  }
}

const setCloudModelDefault = async (row) => {
  if (!row?.id) return
  await request.put(`/admin/llm/cloud-models/${row.id}/default`)
  await loadAdminCloudModels()
  await loadCloudModels()
  if (form.llmProvider === 'cloud' && !form.llmCloudModel) {
    form.llmCloudModel = cloudModels.value?.[0]?.name || ''
  }
  ElMessage.success(TEXT.cloudModelSetDefaultSuccess)
}

const removeCloudModel = async (row) => {
  if (!row?.id) return
  await ElMessageBox.confirm(TEXT.cloudModelDeleteConfirm, TEXT.confirm, { type: 'warning' })
  if (cloudModelManageScope.value === 'private' || !isAdmin.value) {
    await request.delete(`/user/llm/cloud-models/private/${row.id}`)
    await loadPrivateCloudModels()
  } else {
    await request.delete(`/admin/llm/cloud-models/${row.id}`)
    await loadAdminCloudModels()
  }
  await loadCloudModels()
  if (form.llmCloudModel && !cloudModels.value.some((item) => item?.name === form.llmCloudModel)) {
    form.llmCloudModel = cloudModels.value?.[0]?.name || ''
  }
  ElMessage.success(TEXT.cloudModelDeleted)
}

const removePrivateCloudModel = async (row) => {
  cloudModelManageScope.value = 'private'
  await removeCloudModel(row)
}

const changePassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.confirmPassword) {
    ElMessage.warning(TEXT.passwordRequired)
    return
  }
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 20) {
    ElMessage.warning(TEXT.passwordLength)
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning(TEXT.passwordMismatch)
    return
  }
  pwdSaving.value = true
  try {
    await request.put('/user/password', {
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    passwordDialogVisible.value = false
    resetPasswordForm()
    ElMessage.success(TEXT.passwordChanged)
  } finally {
    pwdSaving.value = false
  }
}

const checkLocalHealth = async () => {
  localHealthChecking.value = true
  try {
    const res = await request.get('/user/llm/local-health', { timeout: 30000 })
    localHealthResult.value = res?.data || null
  } finally {
    localHealthChecking.value = false
  }
}

const checkCloudHealth = async () => {
  cloudHealthChecking.value = true
  try {
    const res = await request.get('/user/llm/cloud-health', { params: { model: form.llmCloudModel || undefined }, timeout: 30000 })
    cloudHealthResult.value = res?.data || null
    cloudModelName.value = res?.data?.model || cloudModelName.value
  } finally {
    cloudHealthChecking.value = false
  }
}

const loadLastUsedModel = async () => {
  try {
    const res = await request.get('/user/llm/last-used', { timeout: 10000 })
    lastUsed.value = res?.data || null
  } catch (error) {
    lastUsed.value = null
  }
}

const providerLabel = (provider) => {
  if (provider === 'cloud') return TEXT.providerCloud
  if (provider === 'local') return TEXT.providerLocal
  return TEXT.providerUnknown
}

const cloudProviderLabel = (provider) => {
  const value = String(provider || '').trim().toLowerCase()
  if (!value) return cloudProviderLabels.value.bailian || '\u767e\u70bc'
  return cloudProviderLabels.value[value] || value
}

const formatTimestamp = (timestamp) => {
  const n = Number(timestamp)
  if (!Number.isFinite(n) || n <= 0) {
    return TEXT.timeUnknown
  }
  const d = new Date(n)
  if (Number.isNaN(d.getTime())) {
    return TEXT.timeUnknown
  }
  return d.toLocaleString()
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  syncFromStore()
  await loadLocalModels()
  await loadCloudModels()
  if (isAdmin.value) {
    await loadAdminCloudModels()
  } else {
    await loadPrivateCloudModels()
  }
  await loadLastUsedModel()
  try {
    const res = await request.get('/user/llm/cloud-health', { params: { model: form.llmCloudModel || undefined }, timeout: 30000 })
    cloudModelName.value = res?.data?.model || ''
  } catch (error) {
    cloudModelName.value = ''
  }
})

onUnmounted(() => {
  revokePreview()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  justify-content: center;
}

.card {
  width: min(860px, 100%);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 22px;
}

.card h2 {
  margin: 0 0 18px;
  color: var(--color-primary-strong);
}

.row {
  margin-bottom: 20px;
}

.label {
  display: block;
  margin-bottom: 10px;
  color: var(--color-muted);
  font-size: 13px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-input {
  font-size: 13px;
  color: var(--color-muted);
}

.style-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.style-card {
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-surface);
  padding: 14px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.style-card strong {
  color: var(--color-primary-strong);
}

.style-card small {
  color: var(--color-muted-soft);
}

.style-card.active {
  border-color: var(--color-accent);
  background: var(--color-warning-soft);
}

.health-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.health-btn {
  font-weight: 600;
}

.health-summary {
  color: var(--color-muted-strong);
  font-size: 13px;
}

.admin-model-table {
  margin-top: 10px;
}

.model-overview {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.model-card {
  border: 1px solid var(--color-border-soft);
  border-radius: 10px;
  background: var(--color-surface-soft);
  padding: 10px 12px;
}

.model-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.model-card-title {
  font-size: 12px;
  color: var(--color-muted);
}

.model-card-model {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary-strong);
  line-height: 1.4;
  word-break: break-word;
}

.model-card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-muted);
}

.model-card-state {
  margin-top: 6px;
  font-size: 12px;
  color: #2c7b55;
}

.model-card-state.mismatch {
  color: #b4503d;
}

.security-panel {
  margin-top: 10px;
  margin-bottom: 28px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--color-surface-soft);
}

.security-text h3 {
  margin: 0;
  font-size: 15px;
  color: var(--color-primary-strong);
}

.security-text p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-muted);
}

.save-btn {
  font-weight: 700;
}

@media (max-width: 900px) {
  .style-grid,
  .provider-grid,
  .model-overview {
    grid-template-columns: 1fr;
  }

  .security-panel {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

















