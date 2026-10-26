<template>
  <div class="chat-view">
    <el-card class="chat-container">
      <template #header>
        <div class="chat-header">
          <span>AI 知识助手</span>
          <div class="header-actions">
            <el-tag v-if="streaming" type="success" effect="plain" size="small">流式输出中</el-tag>
            <el-select v-model="currentClubId" placeholder="选择社团" size="small" style="width: 150px">
              <el-option v-for="club in clubList" :key="club.id" :value="club.id" :label="club.clubName" />
            </el-select>
          </div>
        </div>
      </template>

      <div class="messages" ref="messagesRef">
        <div v-for="(msg, index) in messages" :key="index"
             :class="['message', msg.role]">
          <div class="message-content">
            <div class="message-text" v-html="formatContent(msg.content)"></div>
            <div v-if="msg.sources && msg.sources.length" class="message-sources">
              <el-divider content-position="left">参考来源</el-divider>
              <div v-for="(src, i) in msg.sources" :key="i" class="source-item">
                <el-tag size="small">{{ src.doc_type }}</el-tag>
                <span>{{ src.title }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="loading && !streaming" class="message assistant">
          <div class="message-content">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span class="status-text">{{ statusMessage || '正在思考...' }}</span>
          </div>
        </div>
      </div>

      <div class="input-area">
        <el-input v-model="inputMessage" placeholder="输入你的问题..." 
                  @keyup.enter="sendMessage" :disabled="loading">
          <template #append>
            <el-button @click="sendMessage" :loading="loading">发送</el-button>
          </template>
        </el-input>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { aiChatStream } from '@/api/ai'
import { readSSEStream } from '@/utils/sse'
import { listClubs } from '@/api/club'

const messagesRef = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const streaming = ref(false)
const sessionId = ref(null)
const currentClubId = ref(1)
const statusMessage = ref('')
const clubList = ref([])
let abortController = null

watch(currentClubId, () => {
  abortController?.abort()
  sessionId.value = null
  messages.value = []
})

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

// 仅允许的白名单标签，其他一律移除
const ALLOWED_TAGS = ['BR', 'STRONG', 'EM', 'CODE', 'PRE', 'P', 'UL', 'OL', 'LI']
const ALLOWED_ATTRS = []

const sanitizeHtml = (html) => {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const walker = document.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT)
  const toRemove = []
  while (walker.nextNode()) {
    const el = walker.currentNode
    if (!ALLOWED_TAGS.includes(el.tagName)) {
      // 保留子节点，移除标签本身
      while (el.firstChild) {
        el.parentNode.insertBefore(el.firstChild, el)
      }
      toRemove.push(el)
    } else {
      // 移除所有非白名单属性
      for (const attr of Array.from(el.attributes)) {
        if (!ALLOWED_ATTRS.includes(attr.name)) {
          el.removeAttribute(attr.name)
        }
      }
    }
  }
  toRemove.forEach(el => el.parentNode?.removeChild(el))
  return doc.body.innerHTML
}

const escapeHtml = (str) => {
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

const formatContent = (content) => {
  if (!content) return ''
  // 先转义 HTML 防止 XSS，再应用有限的格式化标签
  const escaped = escapeHtml(content)
    .replace(/\[来源(\d+)\]/g, '<strong>[来源$1]</strong>')
    .replace(/\n/g, '<br>')
  // 最终白名单过滤，确保只有允许的标签残留
  return sanitizeHtml(escaped)
}

const sendMessage = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || loading.value) return

  messages.value.push({ role: 'user', content: msg })
  inputMessage.value = ''
  loading.value = true
  streaming.value = false
  statusMessage.value = '正在分析您的问题...'
  scrollToBottom()

  try {
    // 取消上一次未完成的请求
    if (abortController) abortController.abort()
    abortController = new AbortController()

    const response = await aiChatStream({
      clubId: currentClubId.value,
      sessionId: sessionId.value,
      message: msg
    }, abortController.signal)

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    // SSE 流式读取
    streaming.value = true

    // 添加空的助手消息占位
    messages.value.push({ role: 'assistant', content: '', sources: [] })
    const msgIndex = messages.value.length - 1

    await readSSEStream(response, {
      onSession: (id) => { sessionId.value = id },
      onToken: (content) => {
        messages.value[msgIndex] = { ...messages.value[msgIndex], content }
        scrollToBottom()
      },
      onDone: (doneSources) => {
        messages.value[msgIndex] = { ...messages.value[msgIndex], sources: doneSources }
      },
      onStatus: (content) => {
        statusMessage.value = content
      },
      onError: (content) => {
        messages.value[msgIndex] = { ...messages.value[msgIndex], content: `❗ ${content}` }
      }
    })
  } catch (e) {
    if (e.name === 'AbortError') return  // 用户主动取消，不报错
    messages.value.push({
      role: 'assistant',
      content: '抱歉，服务暂时不可用，请稍后重试。'
    })
  } finally {
    abortController = null
    loading.value = false
    streaming.value = false
    statusMessage.value = ''
    scrollToBottom()
  }
}

// 加载社团列表
onMounted(async () => {
  try {
    const res = await listClubs({ pageNum: 1, pageSize: 100 })
    clubList.value = res.data?.list || [{ id: 1, clubName: '默认社团' }]
    if (clubList.value.length > 0) {
      currentClubId.value = clubList.value[0].id
    }
  } catch (e) {
    clubList.value = [{ id: 1, clubName: '默认社团' }]
  }
})

// 组件卸载时取消未完成的请求
onUnmounted(() => {
  if (abortController) abortController.abort()
})
</script>

<style scoped>
.chat-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  max-height: calc(100vh - 250px);
}
.message {
  margin-bottom: 16px;
  display: flex;
}
.message.user {
  justify-content: flex-end;
}
.message.assistant {
  justify-content: flex-start;
}
.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
}
.message.user .message-content {
  background: var(--primary-color);
  color: white;
}
.message.assistant .message-content {
  background: #f4f4f5;
}
.message-text {
  line-height: 1.6;
  word-break: break-word;
}
.message-sources {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.source-item {
  margin: 4px 0;
}
.input-area {
  padding: 16px;
  border-top: 1px solid #ebeef5;
}
.status-text {
  color: #909399;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
