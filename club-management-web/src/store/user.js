/**
 * 登录状态管理：从 localStorage 恢复 Token、用户资料和角色，封装登录、刷新资料与退出清理。
 * 登录和退出同时清空聊天 Store，避免不同登录会话之间保留对话。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getCurrentUser } from '@/api/user'
import { useChatStore } from './chat'

export const useUserStore = defineStore('user', () => {
  // 安全解析 localStorage，防止非法 JSON 导致白屏
  const safeParse = (key, fallback) => {
    try {
      const raw = localStorage.getItem(key)
      return raw ? JSON.parse(raw) : fallback
    } catch {
      localStorage.removeItem(key)
      return fallback
    }
  }

  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(safeParse('userInfo', null))
  const roles = ref(safeParse('roles', []))

  async function login(loginForm) {
    const res = await loginApi(loginForm)
    // 仅在登录接口成功后替换账号状态；新登录会话从空聊天历史开始。
    useChatStore().clear()
    token.value = res.data.token
    userInfo.value = {
      userId: res.data.userId,
      username: res.data.username,
      realName: res.data.realName
    }
    roles.value = res.data.roles
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.setItem('roles', JSON.stringify(roles.value))
    return res
  }

  async function fetchUserInfo() {
    const res = await getCurrentUser()
    userInfo.value = res.data
    // /auth/me 返回角色对象列表，将其转换为路由和菜单使用的角色编码数组。
    roles.value = res.data.roles?.map(r => r.roleCode) || []
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.setItem('roles', JSON.stringify(roles.value))
    return res
  }

  function logout() {
    // 内存与浏览器持久化同时清理；聊天清理还会使进行中的旧请求结果失效。
    useChatStore().clear()
    token.value = ''
    userInfo.value = null
    roles.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('roles')
  }

  return { token, userInfo, roles, login, fetchUserInfo, logout }
})
