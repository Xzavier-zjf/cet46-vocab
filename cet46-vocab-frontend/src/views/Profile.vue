<template>
  <section class="profile-page">
    <section class="card">
      <h2>个人资料</h2>

      <div class="row">
        <span class="label">昵称</span>
        <span class="value">{{ userStore.nickname || '未设置' }}</span>
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
        <span class="label">每日目标</span>
        <el-input-number v-model="form.dailyTarget" :min="1" :max="100" />
      </div>

      <el-button class="save-btn" :loading="saving" @click="savePreference">保存设置</el-button>
    </section>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const saving = ref(false)

const styles = [
  { value: 'academic', title: '学术风', desc: '偏重词源与规则解释' },
  { value: 'story', title: '故事风', desc: '联想式情景记忆' },
  { value: 'sarcastic', title: '毒舌风', desc: '高对比、强记忆点' }
]

const form = reactive({
  llmStyle: 'story',
  dailyTarget: 20
})

const syncFromStore = () => {
  form.llmStyle = userStore.llmStyle || 'story'
  form.dailyTarget = userStore.dailyTarget || 20
}

const savePreference = async () => {
  saving.value = true
  try {
    await request.put('/user/preference', {
      llmStyle: form.llmStyle,
      dailyTarget: form.dailyTarget
    })
    userStore.llmStyle = form.llmStyle
    userStore.dailyTarget = form.dailyTarget
    ElMessage.success('偏好设置已更新')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await userStore.fetchUserInfo()
  syncFromStore()
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
  color: #1A2B4A;
}

.row {
  margin-bottom: 18px;
}

.label {
  display: block;
  margin-bottom: 10px;
  color: #6B7A8D;
  font-size: 13px;
}

.value {
  color: #1A2B4A;
  font-weight: 600;
}

.style-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  color: #1A2B4A;
}

.style-card small {
  color: #8896A8;
}

.style-card.active {
  border-color: #C9A84C;
  background: rgba(201, 168, 76, 0.12);
}

.save-btn {
  background: #1A2B4A;
  border-color: #1A2B4A;
  color: #fff;
}

@media (max-width: 900px) {
  .style-grid {
    grid-template-columns: 1fr;
  }
}
</style>
