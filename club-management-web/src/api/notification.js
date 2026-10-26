import request from './request'

export function myNotifications(params) {
  return request.get('/notifications/me', { params })
}

export function markAsRead(id) {
  return request.put(`/notifications/${id}/read`)
}

export function markAllAsRead() {
  return request.put('/notifications/read-all')
}

export function getUnreadCount() {
  return request.get('/notifications/me/unread-count')
}

export function sendNotification(data) {
  return request.post('/notifications', data)
}
