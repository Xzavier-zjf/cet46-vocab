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
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            class="login-btn"
            :loading="loading"
            type="primary"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer-link">
        没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return
    }
    loading.value = true
    try {
      const res = await login(form)
      if (res?.code !== 200) {
        ElMessage.error(res?.message || '登录失败')
        return
      }
      userStore.setUserInfo(res.data || {})
      await userStore.fetchUserInfo()
      const redirect = route.query.redirect ? decodeURIComponent(route.query.redirect) : '/dashboard'
      router.push(redirect)
      ElMessage.success('登录成功')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: #1a2b4a;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 400px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.28);
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
  color: #c9a84c;
  font-weight: 700;
}

.subtitle {
  margin-top: 8px;
  margin-bottom: 0;
  color: #60708b;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  border: none;
  background: #1a2b4a;
}

.login-btn:hover {
  background: #13203a;
}

.footer-link {
  margin-top: 6px;
  text-align: center;
  color: #60708b;
  font-size: 14px;
}

.footer-link a {
  color: #1a2b4a;
  font-weight: 600;
  text-decoration: none;
}
</style>
