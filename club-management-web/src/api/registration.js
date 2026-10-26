import request from './request'

export function register(data) {
  return request.post('/registrations', data)
}

export function cancelRegistration(activityId) {
  return request.delete(`/registrations/${activityId}`)
}

export function myRegistrations(params) {
  return request.get('/registrations/me', { params })
}

export function listRegistrations(activityId, params) {
  return request.get(`/activities/${activityId}/registrations`, { params })
}

export function generateSignInCode(id) {
  return request.post(`/activities/${id}/sign-in/code`)
}

export function signIn(data) {
  return request.post('/sign-in', data)
}

export function getSignInStatistics(id) {
  return request.get(`/activities/${id}/sign-in/statistics`)
}
