import request from './request'

export function listActivities(params) {
  return request.get('/activities', { params })
}

export function getActivityDetail(id) {
  return request.get(`/activities/${id}`)
}

export function createActivity(data) {
  return request.post('/activities', data)
}

export function updateActivity(id, data) {
  return request.put(`/activities/${id}`, data)
}

export function deleteActivity(id) {
  return request.delete(`/activities/${id}`)
}

export function submitForApproval(id) {
  return request.post(`/activities/${id}/submit`)
}

export function approveActivity(id, data) {
  return request.post(`/activities/${id}/approve`, data)
}

export function startActivity(id) {
  return request.put(`/activities/${id}/status/in-progress`)
}

export function completeActivity(id, data) {
  return request.put(`/activities/${id}/complete`, data)
}
