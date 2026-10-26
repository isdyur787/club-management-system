import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      unreadCount.value = res.data.count
    } catch {
      // 忽略
    }
  }

  function reset() {
    unreadCount.value = 0
  }

  return { unreadCount, fetchUnreadCount, reset }
})
