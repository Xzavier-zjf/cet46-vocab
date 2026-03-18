<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="title-wrap">
        <h1 class="project-title">CET46 Vocabulary</h1>
        <p class="subtitle">智能记忆系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-20位用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="6-20位密码"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
          />
        </el-form-item>
        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="输入昵称（可选）" />
        </el-form-item>
        <el-form-item>
          <BtnPrimary
            class="register-btn"
            :loading="loading"
            type="primary"
            @click="handleRegister"
          >
            注册并登录
          </BtnPrimary>
        </el-form-item>
      </el-form>

      <div class="footer-link">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import BtnPrimary from '@/components/common/BtnPrimary.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请确认密码'))
    return
  }
  if (value !== form.password) {
    callback(new Error('两次输入密码不一致'))
    return
  }
  callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度4-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

const handleRegister = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }

    loading.value = true
    try {
      const registerRes = await register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined
      })
      if (registerRes?.code !== 200) {
        ElMessage.error(registerRes?.message || '注册失败')
        return
      }

      const loginRes = await login({
        username: form.username,
        password: form.password
      })
      if (loginRes?.code !== 200) {
        ElMessage.error(loginRes?.message || '自动登录失败，请手动登录')
        router.push('/login')
        return
      }

      userStore.setUserInfo(loginRes.data || {})
      await userStore.fetchUserInfo()
      ElMessage.success('注册成功')
      router.push('/onboarding')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: var(--bg-gradient-1), var(--bg-gradient-2), var(--color-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 400px;
  background: var(--color-surface);
  border-radius: 24px;
  box-shadow: var(--shadow-card);
  padding: 36px 34px 28px;
}

.title-wrap {
  text-align: center;
  margin-bottom: 24px;
}

.project-title {
  margin: 0;
  font-size: 30px;
  letter-spacing: 0.6px;
  color: var(--color-accent);
  font-weight: 700;
}

.subtitle {
  margin-top: 8px;
  margin-bottom: 0;
  color: var(--color-muted);
  font-size: 14px;
}

.register-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  font-weight: 700;
}

.footer-link {
  margin-top: 6px;
  text-align: center;
  color: var(--color-muted);
  font-size: 14px;
}

.footer-link a {
  color: var(--color-primary-strong);
  font-weight: 600;
  text-decoration: none;
}
</style>
