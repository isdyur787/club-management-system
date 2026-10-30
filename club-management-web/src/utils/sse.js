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
    // fetch 不经过 Axios 拦截器，因此在这里单独处理 HTTP 失败和登录失效。
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
 * @param {function} [handlers.onSession] - 收到服务端会话标识: (sessionId: string) => void
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

    // 网络分块不等于事件边界：流式解码避免截断中文，缓冲区保留未完整收到的行。
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || '' // 保留不完整的最后一行

    for (const line of lines) {
      if (!line.startsWith('data:')) continue
      const data = line.startsWith('data: ') ? line.substring(6).trim() : line.substring(5).trim()

      // 协议中的结构化 done 事件携带来源；单独的 [DONE] 标记无需解析 JSON。
      if (data === '[DONE]') continue

      try {
        const event = JSON.parse(data)

        switch (event.type) {
          case 'session':
            onSession?.(event.sessionId)
            break
          case 'token':
            // 回调接收累计全文，页面可直接替换显示内容，不必再自行拼接。
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
            // 错误事件交给调用方展示；是否终止读取由后续 done 或网络流结束决定。
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
