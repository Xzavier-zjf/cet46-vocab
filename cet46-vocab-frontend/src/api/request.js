import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error?.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }

    const status = error?.response?.status
    const userStore = useUserStore()
    if (status === 401) {
      userStore.clearUserInfo()
      router.push(`/login?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`)
      return Promise.reject(error)
    }

    const msg =
      error?.response?.data?.message ||
      error?.message ||
      '请求失败，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
