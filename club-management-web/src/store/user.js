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
    roles.value = res.data.roles?.map(r => r.roleCode) || []
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.setItem('roles', JSON.stringify(roles.value))
    return res
  }

  function logout() {
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
