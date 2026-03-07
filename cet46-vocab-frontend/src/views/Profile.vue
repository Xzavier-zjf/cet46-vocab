<template>
  <section class="profile-page">
    <section class="card">
      <h2>我的资料</h2>

      <div class="row">
        <span class="label">头像</span>
        <div class="avatar-row">
          <el-avatar :size="72" :src="avatarPreview || userStore.avatar || undefined">
            {{ avatarText }}
          </el-avatar>
          <input class="avatar-input" type="file" accept="image/*" @change="handleAvatarChange" />
        </div>
      </div>

      <div class="row">
        <span class="label">昵称</span>
        <el-input v-model="form.nickname" maxlength="50" placeholder="请输入昵称" />
      </div>

      <div class="row">
        <span class="label">学习风格</span>
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
        <span class="label">模型来源</span>
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
      </div>

      <div class="row">
        <span class="label">本地连通自检</span>
        <div class="health-actions">
          <el-button class="health-btn" :loading="localHealthChecking" @click="checkLocalHealth">一键检测本地模型</el-button>
          <span v-if="localHealthResult" class="health-summary">
            {{ localHealthResult.message }}
            <template v-if="localHealthResult.latencyMs">（{{ localHealthResult.latencyMs }}ms）</template>
          </span>
        </div>
      </div>

      <div class="row">
        <span class="label">云端连通自检</span>
        <div class="health-actions">
          <el-button class="health-btn" :loading="cloudHealthChecking" @click="checkCloudHealth">一键检测云端API</el-button>
          <span v-if="cloudHealthResult" class="health-summary">
            {{ cloudHealthResult.message }}
            <template v-if="cloudHealthResult.latencyMs">（{{ cloudHealthResult.latencyMs }}ms）</template>
          </span>
        </div>
      </div>

      <div class="row" v-if="!isAdmin">
        <span class="label">每日目标</span>
        <el-input-number v-model="form.dailyTarget" :min="1" :max="100" />
      </div>

      <div class="security-panel">
        <div class="security-text">
          <h3>账号安全</h3>
          <p>定期更新密码可以提高账号安全性。</p>
        </div>
        <el-button class="health-btn" @click="openPasswordDialog">修改密码</el-button>
      </div>

      <el-button class="save-btn" :loading="saving" @click="saveAll">保存设置</el-button>
    </section>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（6-20位）" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const saving = ref(false)
const pwdSaving = ref(false)
const passwordDialogVisible = ref(false)
const localHealthChecking = ref(false)
const localHealthResult = ref(null)
const cloudHealthChecking = ref(false)
const cloudHealthResult = ref(null)
const avatarFile = ref(null)
const avatarPreview = ref('')
const objectUrl = ref('')

const styles = [
  { value: 'academic', title: '学术风格', desc: '偏规则与词源解释' },
  { value: 'story', title: '故事风格', desc: '偏场景联想记忆' },
  { value: 'sarcastic', title: '吐槽风格', desc: '高反差记忆点' }
]

const providers = [
  { value: 'local', title: '本地模型', desc: 'Ollama 本地推理' },
  { value: 'cloud', title: '云端API', desc: '云模型效果更稳' }
]

const form = reactive({
  nickname: '',
  llmStyle: 'story',
  llmProvider: 'local',
  dailyTarget: 20
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarText = computed(() => (form.nickname || userStore.nickname || '我').slice(0, 1))
const isAdmin = computed(() => userStore.role === 'ADMIN')

const syncFromStore = () => {
  form.nickname = userStore.nickname || ''
  form.llmStyle = userStore.llmStyle || 'story'
  form.llmProvider = userStore.llmProvider || 'local'
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
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 2MB')
    return
  }
  avatarFile.value = file
  revokePreview()
  objectUrl.value = URL.createObjectURL(file)
  avatarPreview.value = objectUrl.value
}

const saveAll = async () => {
  const nickname = form.nickname.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
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
      llmProvider: form.llmProvider
    }
    if (!isAdmin.value) {
      preferencePayload.dailyTarget = form.dailyTarget
    }
    await request.put('/user/preference', preferencePayload)
    await userStore.fetchUserInfo()
    syncFromStore()
    avatarFile.value = null
    avatarPreview.value = ''
    revokePreview()
    ElMessage.success('资料已更新')
  } finally {
    saving.value = false
  }
}

const changePassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword || !pwdForm.confirmPassword) {
    ElMessage.warning('请完整填写密码信息')
    return
  }
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 20) {
    ElMessage.warning('新密码长度需为 6-20 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
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
    ElMessage.success('密码修改成功')
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
    const res = await request.get('/user/llm/cloud-health', { timeout: 30000 })
    cloudHealthResult.value = res?.data || null
  } finally {
    cloudHealthChecking.value = false
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  syncFromStore()
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
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 22px;
}

.card h2 {
  margin: 0 0 18px;
  color: #1a2b4a;
}

.row {
  margin-bottom: 20px;
}

.label {
  display: block;
  margin-bottom: 10px;
  color: #6b7a8d;
  font-size: 13px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-input {
  font-size: 13px;
  color: #6b7a8d;
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
  border: 1px solid #d8e0ea;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.style-card strong {
  color: #1a2b4a;
}

.style-card small {
  color: #8896a8;
}

.style-card.active {
  border-color: #c9a84c;
  background: rgba(201, 168, 76, 0.12);
}

.health-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.health-btn {
  border-color: #1a2b4a;
  color: #1a2b4a;
}

.health-summary {
  color: #4d5c70;
  font-size: 13px;
}

.security-panel {
  margin-top: 10px;
  margin-bottom: 28px;
  border: 1px solid #d8e0ea;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #f8fbff;
}

.security-text h3 {
  margin: 0;
  font-size: 15px;
  color: #1a2b4a;
}

.security-text p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.save-btn {
  background: #1a2b4a;
  border-color: #1a2b4a;
  color: #fff;
}

@media (max-width: 900px) {
  .style-grid,
  .provider-grid {
    grid-template-columns: 1fr;
  }

  .security-panel {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

