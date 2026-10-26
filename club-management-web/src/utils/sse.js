/**
 * SSE (Server-Sent Events) 工具函数
 * 封装 fetch-based POST SSE 请求，统一 token 管理和 401 处理
 */
import { useUserStore } from '@/store'
import router from '@/router'
import { API_BASE_URL } from '@/api/config'

/**
 * 发起 SSE 流式请求（POST）
 * @param {string} url - 请求路径（不含 baseURL 前缀）
 * @param {object} data - 请求体 JSON 数据
 * @param {AbortSignal} [signal] - 可选的 AbortSignal 用于取消请求
 * @returns {Promise<Response>} fetch Response 对象
 */
export async function ssePost(url, data, signal) {
  const userStore = useUserStore()
  const token = userStore.token || ''

  const response = await fetch(API_BASE_URL + url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : ''
    },
    body: JSON.stringify(data),
    signal: signal || undefined
  })

  if (!response.ok) {
    if (response.status === 401) {
      userStore.logout()
      router.push('/login')
    }
    throw new Error(`HTTP ${response.status}: ${response.statusText}`)
  }

  return response
}

/**
 * 逐行读取 SSE 流，回调解析后的事件对象
 * @param {Response} response - fetch Response（ReadableStream）
 * @param {object} handlers - 事件处理回调
 * @param {function} [handlers.onToken] - 收到 token 片段: (content: string) => void
 * @param {function} [handlers.onDone] - 收到完成事件: (sources: array) => void
 * @param {function} [handlers.onStatus] - 收到状态更新: (content: string) => void
 * @param {function} [handlers.onError] - 收到错误事件: (content: string) => void
 * @returns {Promise<{ sources: array, content: string }>} 最终结果
 */
export async function readSSEStream(response, { onToken, onDone, onStatus, onError, onSession }) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let content = ''
  let sources = []

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || '' // 保留不完整的最后一行

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const data = line.startsWith('data: ') ? line.substring(6).trim() : line.substring(5).trim()

      if (data === '[DONE]') continue

      try {
        const event = JSON.parse(data)

        switch (event.type) {
          case 'session':
            onSession?.(event.sessionId)
            break
          case 'token':
            content += event.content
            onToken?.(content)
            break
          case 'done':
            sources = event.sources || []
            onDone?.(sources)
            return { sources, content }
          case 'status':
            onStatus?.(event.content)
            break
          case 'error':
            onError?.(event.content)
            break
        }
      } catch {
        // 忽略非 JSON 行
      }
    }
  }

  return { sources, content }
}
