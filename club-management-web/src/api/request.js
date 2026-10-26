import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store'
import router from '@/router'
import { API_BASE_URL } from './config'

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
})

// 请求拦截器：自动附加 JWT
service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器：统一错误处理
service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      // 401: 跳转登录（SPA 内导航）
      if (res.code === 40002) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    // 解析后端返回的错误消息，而非仅用 Axios 默认消息
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    // HTTP 401 状态码也触发登出（与业务 code 40002 双重监听）
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default service
