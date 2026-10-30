/**
 * 统一 Axios 客户端：配置 API 前缀与超时，附加登录 Token，解析业务响应并集中提示错误。
 * 业务登录失效码 40002 或 HTTP 401 会清理登录状态并跳转登录页。
 */
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
    // HTTP 成功仍可能是业务失败；调用方仅收到 code=200 的完整业务响应对象。
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      // 后端业务码 40002 表示登录状态失效，清理缓存后使用 SPA 导航跳转。
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
