<template>
  <div class="auth-page" :class="{ 'is-dark': isDark }">
    <button
      class="theme-toggle-auth"
      type="button"
      :aria-label="isDark ? '切换为浅色模式' : '切换为深色模式'"
      @click="toggleTheme"
    >
      <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon>
    </button>

    <div class="bg-layer" aria-hidden="true">
      <span v-for="item in bubbles" :key="item.id" class="bubble" :style="item.style"></span>
      <div class="grid-wave"></div>
    </div>

    <div class="register-shell">
      <section class="brand-panel">
        <p class="brand-tag">CET-4 / CET-6</p>
        <h1 class="brand-title">加入词汇冒险，解锁记忆新关卡</h1>
        <p class="brand-subtitle">创建账号后即可开启个性化学习路径与闯关计划。</p>

        <div class="chip-cloud">
          <span v-for="word in teaserWords" :key="word" class="chip">{{ word }}</span>
        </div>
      </section>

      <section class="auth-card">
        <div class="title-wrap">
          <h2 class="project-title">创建账号</h2>
          <p class="subtitle">注册 CET46 Vocabulary</p>
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
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import BtnPrimary from '@/components/common/BtnPrimary.vue'
import { Moon, Sunny } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
const loading = ref(false)
const isDark = computed(() => themeStore.isDark)
const toggleTheme = () => themeStore.toggleTheme()
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
    { min: 4, max: 20, message: '用户名长度 4-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

const bubbles = [
  { id: 1, style: { width: '220px', height: '220px', left: '-60px', top: '-30px', animationDelay: '0s' } },
  { id: 2, style: { width: '160px', height: '160px', right: '8%', top: '10%', animationDelay: '1.4s' } },
  { id: 3, style: { width: '260px', height: '260px', right: '-80px', bottom: '-60px', animationDelay: '0.8s' } },
  { id: 4, style: { width: '120px', height: '120px', left: '12%', bottom: '14%', animationDelay: '2s' } }
]

const teaserWords = ['vocabulary', 'challenge', 'memory', 'upgrade', 'fluency', 'victory']

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
      themeStore.setActiveIdentity(userStore.userId || userStore.username || '')
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
  --bubble-bg-start: rgba(255, 255, 255, 0.78);
  --bubble-bg-end: rgba(255, 255, 255, 0.2);
  --bubble-border: rgba(255, 255, 255, 0.68);
  --grid-color: rgba(35, 62, 114, 0.08);
  --grid-glow: rgba(103, 144, 226, 0.16);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 24px;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 12% 16%, #f6d8b8 0%, transparent 38%),
    radial-gradient(circle at 86% 82%, #d7e8ff 0%, transparent 40%),
    linear-gradient(130deg, #f7efe2 0%, #eef5ff 50%, #f6eee9 100%);
  font-family: 'Space Grotesk', 'Avenir Next', 'Segoe UI', sans-serif;
}

.theme-toggle-auth {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  background: rgba(255, 255, 255, 0.62);
  color: #2a436f;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 20px rgba(31, 59, 115, 0.14);
  z-index: 5;
  transition: transform 0.2s ease, background-color 0.2s ease;
}

.theme-toggle-auth:hover {
  transform: translateY(-1px);
}

.bg-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bubble {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--bubble-bg-start), var(--bubble-bg-end));
  border: 1px solid var(--bubble-border);
  backdrop-filter: blur(3px);
  animation: float 7.5s ease-in-out infinite;
  box-shadow: inset 0 0 18px rgba(255, 255, 255, 0.2);
}

.grid-wave {
  position: absolute;
  inset: -30% -20% auto;
  height: 140%;
  background-image:
    linear-gradient(var(--grid-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-color) 1px, transparent 1px);
  background-size: 38px 38px;
  filter: drop-shadow(0 0 24px var(--grid-glow));
  transform: perspective(700px) rotateX(62deg);
  transform-origin: top;
  animation: drift 12s linear infinite;
}

.register-shell {
  position: relative;
  width: min(980px, 100%);
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.64);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 20px 60px rgba(30, 48, 95, 0.15);
  backdrop-filter: blur(10px);
  overflow: hidden;
  display: grid;
  grid-template-columns: 1.02fr 0.98fr;
}

.brand-panel {
  padding: 56px 48px;
  color: #233e72;
  background:
    radial-gradient(circle at 10% 8%, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.2) 55%),
    linear-gradient(145deg, #dce9ff, #f5e1c8);
}

.brand-tag {
  margin: 0;
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #2b4f90;
  background: rgba(255, 255, 255, 0.8);
}

.brand-title {
  margin: 18px 0 10px;
  font-size: clamp(26px, 3.8vw, 38px);
  line-height: 1.18;
  font-weight: 800;
}

.brand-subtitle {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: rgba(35, 62, 114, 0.82);
}

.chip-cloud {
  margin-top: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  color: #274275;
  border: 1px solid rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.7);
  animation: pop 3.6s ease-in-out infinite;
}

.chip:nth-child(2n) { animation-delay: 0.4s; }
.chip:nth-child(3n) { animation-delay: 0.9s; }

.auth-card {
  background: rgba(255, 255, 255, 0.85);
  padding: 34px 34px 26px;
}

.title-wrap {
  text-align: center;
  margin-bottom: 16px;
}

.project-title {
  margin: 0;
  color: #1f3b73;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.subtitle {
  margin: 8px 0 0;
  color: #5f6f8d;
  font-size: 14px;
}

.register-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
}

.footer-link {
  margin-top: 6px;
  text-align: center;
  color: #5f6f8d;
  font-size: 14px;
}

.footer-link a {
  color: #1f3b73;
  font-weight: 700;
  text-decoration: none;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-18px); }
}

@keyframes drift {
  0% { transform: perspective(700px) rotateX(62deg) translateY(0); }
  100% { transform: perspective(700px) rotateX(62deg) translateY(38px); }
}

@keyframes pop {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-4px) scale(1.03); }
}

.auth-page.is-dark {
  --bubble-bg-start: rgba(56, 88, 142, 0.46);
  --bubble-bg-end: rgba(28, 45, 78, 0.2);
  --bubble-border: rgba(117, 145, 204, 0.42);
  --grid-color: rgba(145, 173, 224, 0.2);
  --grid-glow: rgba(86, 122, 191, 0.36);
  background:
    radial-gradient(circle at 12% 16%, rgba(52, 76, 122, 0.38) 0%, transparent 42%),
    radial-gradient(circle at 84% 84%, rgba(90, 70, 52, 0.3) 0%, transparent 40%),
    linear-gradient(135deg, #111a2c 0%, #18243b 52%, #1f1a26 100%);
}

.auth-page.is-dark .theme-toggle-auth {
  background: rgba(22, 33, 55, 0.72);
  border-color: rgba(114, 138, 189, 0.45);
  color: #c7d8ff;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.35);
}

.auth-page.is-dark .register-shell {
  background: rgba(17, 27, 44, 0.72);
  border-color: rgba(119, 145, 195, 0.26);
  box-shadow: 0 22px 64px rgba(0, 0, 0, 0.42);
}

.auth-page.is-dark .brand-panel {
  color: #dbe7ff;
  background:
    radial-gradient(circle at 10% 8%, rgba(54, 82, 136, 0.5), rgba(25, 37, 63, 0.24) 55%),
    linear-gradient(145deg, #1b2b4c, #2b2236);
}

.auth-page.is-dark .brand-subtitle,
.auth-page.is-dark .subtitle,
.auth-page.is-dark .footer-link {
  color: #99add0;
}

.auth-page.is-dark .brand-tag,
.auth-page.is-dark .chip {
  color: #d0defa;
  background: rgba(20, 33, 58, 0.65);
  border-color: rgba(122, 146, 194, 0.35);
}

.auth-page.is-dark .auth-card {
  background: rgba(16, 24, 40, 0.82);
}

.auth-page.is-dark .project-title,
.auth-page.is-dark .footer-link a {
  color: #d8e6ff;
}

@media (max-width: 920px) {
  .register-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding: 34px 24px 22px;
  }

  .auth-card {
    padding: 30px 22px 20px;
  }
}
</style>
