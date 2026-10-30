/**
 * 扩展业务接口集合：封装打卡/签退、积分、AI 问答、报告、汇编、知识库与文件上传。
 * 普通接口复用 Axios，流式问答交给 SSE 工具；生成类请求单独延长等待时间。
 */
import request from './request'
import { ssePost } from '@/utils/sse'

// === 打卡 ===
export function createCheckinTask(data) {
  return request.post('/checkin/tasks', data)
}

export function getCheckinTask(activityId) {
  return request.get(`/checkin/tasks/${activityId}`)
}

export function submitCheckinRecord(data) {
  return request.post('/checkin/records', data)
}

export function getCheckinRecords(activityId, params) {
  return request.get(`/checkin/records/${activityId}`, { params })
}

export function getMyCheckinRecord(activityId) {
  return request.get(`/checkin/my/${activityId}`)
}

export function adoptSuggestion(id) {
  return request.patch(`/checkin/records/${id}/adopt`)
}

export function settlePoints(activityId) {
  return request.post(`/checkin/settle/${activityId}`)
}

export function generateCompilation(activityId) {
  // 后端汇总成员反馈并生成汇编；此接口单独将等待时间从默认 30 秒延长到 120 秒。
  return request.post(`/checkin/compilation/generate/${activityId}`, null, { timeout: 120000 })
}

export function getCompilation(activityId) {
  return request.get(`/checkin/compilation/${activityId}`)
}

// === 签退 ===
export function signOut(data) {
  return request.post('/signout', data)
}

// === 积分 ===
export function getPointsClubs() {
  return request.get('/points/clubs')
}

export function getClubPointsLogs(clubId, params) {
  return request.get('/points/activity', { params: { clubId, ...params } })
}

export function getMyPointsLogs(clubId, params) {
  return request.get('/points/my', { params: { clubId, ...params } })
}

export function getPointsRanking(clubId, top = 20) {
  return request.get('/points/ranking', { params: { clubId, top } })
}

export function getPointsSummary(clubId) {
  return request.get('/points/summary', { params: { clubId } })
}

// === AI 对话 ===
export function aiChat(data) {
  return request.post('/ai/chat', data, { timeout: 120000 })
}

export function aiChatStream(data, signal) {
  // 返回原始 Response，消费方需交给 readSSEStream；当前 ChatView 使用上面的 aiChat。
  return ssePost('/ai/chat/stream', data, signal)
}

export function createChatSession(data) {
  return request.post('/ai/chat/sessions', data)
}

// === AI 成果报告 ===
export function generateReport(data) {
  return request.post('/ai/report/generate', data, { timeout: 120000 })
}

export function getReport(activityId) {
  return request.get(`/ai/report/${activityId}`)
}

export function confirmReport(id) {
  return request.post(`/ai/report/${id}/confirm`)
}

export function rejectReport(id, data) {
  return request.post(`/ai/report/${id}/reject`, data)
}

// === AI 经验参考 ===
export function getExperience(data) {
  return request.post('/ai/experience', data, { timeout: 120000 })
}

// === 知识库 ===
export function uploadKnowledgeDocument(formData) {
  return request.post('/ai/knowledge/documents', formData, {
    timeout: 120000,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getKnowledgeDocuments(params) {
  return request.get('/ai/knowledge/documents', { params })
}

export function deleteKnowledgeDocument(id) {
  return request.delete(`/ai/knowledge/documents/${id}`)
}

// === 文件上传 ===
export function uploadFile(formData) {
  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
