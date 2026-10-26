import request from './request'

export function getClubActivityStats(clubId, params) {
  return request.get(`/statistics/clubs/${clubId}/activities`, { params })
}

export function getClubScores(params) {
  return request.get('/statistics/clubs/score', { params })
}

export function getOverview() {
  return request.get('/statistics/overview')
}
