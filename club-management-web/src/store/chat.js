/**
 * AI 会话状态：按账号隔离 sessionStorage，并为每个社团保留消息、服务端会话标识和加载状态。
 * 退出/切换账号时递增请求代次，丢弃旧账号迟到的回复；本模块不直接绑定具体网络接口。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_PREFIX = 'club-ai-chat:'
const emptyConversation = () => ({ sessionId: null, messages: [], loading: false })

export const useChatStore = defineStore('chat', () => {
  const currentClubId = ref(null)
  const conversations = ref({})
  let ownerId = null
  // 账号生命周期的版本号，不同社团切换共享同一版本，退出或更换账号才递增。
  let generation = 0

  function persist() {
    if (!ownerId) return
    try {
      // loading 属于当前请求的临时状态，刷新页面时不能把它恢复为“仍在等待”。
      const saved = Object.fromEntries(Object.entries(conversations.value).map(([id, conversation]) => [id, {
        sessionId: conversation.sessionId,
        messages: conversation.messages
      }]))
      sessionStorage.setItem(STORAGE_PREFIX + ownerId, JSON.stringify({ currentClubId: currentClubId.value, conversations: saved }))
    } catch {
      // 存储空间不足时仍保留 Pinia 中的本次登录记录。
    }
  }

  function initialize(userId) {
    const nextOwner = userId == null ? null : String(userId)
    if (ownerId === nextOwner) return
    generation++
    ownerId = nextOwner
    currentClubId.value = null
    conversations.value = {}
    if (!ownerId) return
    try {
      const saved = JSON.parse(sessionStorage.getItem(STORAGE_PREFIX + ownerId) || 'null')
      if (!saved || typeof saved.conversations !== 'object' || !saved.conversations) return
      // 恢复时只接受社团数字标识及基本合法的消息，损坏缓存不影响进入页面。
      for (const [id, conversation] of Object.entries(saved.conversations)) {
        if (!/^\d+$/.test(id) || !Array.isArray(conversation?.messages)) continue
        conversations.value[id] = {
          sessionId: typeof conversation.sessionId === 'string' ? conversation.sessionId : null,
          messages: conversation.messages.filter(message => ['user', 'assistant'].includes(message?.role) && typeof message.content === 'string'),
          loading: false
        }
      }
      currentClubId.value = Number(saved.currentClubId) || null
    } catch {
      sessionStorage.removeItem(STORAGE_PREFIX + ownerId)
    }
  }

  function getConversation(clubId) {
    if (!clubId) return emptyConversation()
    if (!conversations.value[clubId]) conversations.value[clubId] = emptyConversation()
    return conversations.value[clubId]
  }

  function selectClub(clubId) {
    currentClubId.value = clubId
    if (clubId) getConversation(clubId)
    persist()
  }

  async function sendMessage(clubId, question, request) {
    const message = question.trim()
    if (!ownerId || !clubId || !message) return
    const conversation = getConversation(clubId)
    // 同一社团一次只发送一条；闭包保留发起时的社团，切换页面不改变回复归属。
    if (conversation.loading) return
    const requestGeneration = generation
    conversation.messages.push({ role: 'user', content: message })
    conversation.loading = true
    persist()
    try {
      const response = await request({ clubId, sessionId: conversation.sessionId, message })
      // 网络请求可以继续完成，但旧登录会话的结果不能回写到当前账号。
      if (generation !== requestGeneration) return
      conversation.sessionId = response.data.sessionId
      conversation.messages.push({ role: 'assistant', content: response.data.answer, sources: response.data.sources || [] })
    } catch {
      if (generation !== requestGeneration) return
      conversation.messages.push({ role: 'assistant', content: '抱歉，服务暂时不可用，请稍后重试。' })
    } finally {
      if (generation === requestGeneration) {
        conversation.loading = false
        persist()
      }
    }
  }

  function clear() {
    generation++
    ownerId = null
    currentClubId.value = null
    conversations.value = {}
    // 先收集再删除，避免遍历 sessionStorage 时索引变化而漏删历史。
    const keys = []
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)
      if (key?.startsWith(STORAGE_PREFIX)) keys.push(key)
    }
    keys.forEach(key => sessionStorage.removeItem(key))
  }

  return { currentClubId, conversations, initialize, getConversation, selectClub, sendMessage, clear }
})
