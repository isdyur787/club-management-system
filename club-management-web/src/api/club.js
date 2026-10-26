import request from './request'

export function listClubs(params) {
  return request.get('/clubs', { params })
}

export function getClubDetail(id) {
  return request.get(`/clubs/${id}`)
}

export function createClub(data) {
  return request.post('/clubs', data)
}

export function updateClub(id, data) {
  return request.put(`/clubs/${id}`, data)
}

export function approveClub(id, data) {
  return request.put(`/clubs/${id}/approve`, data)
}

export function joinClub(id, data) {
  return request.post(`/clubs/${id}/members`, data)
}

export function leaveClub(clubId) {
  return request.delete(`/clubs/${clubId}/members/me`)
}

export function listMembers(clubId, params) {
  return request.get(`/clubs/${clubId}/members`, { params })
}

export function removeMember(clubId, userId) {
  return request.delete(`/clubs/${clubId}/members/${userId}`)
}
