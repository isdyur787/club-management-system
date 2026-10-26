import request from './request'

export function listVenues(params) {
  return request.get('/venues', { params })
}

export function getVenueAvailability(id, date) {
  return request.get(`/venues/${id}/availability`, { params: { date } })
}

export function bookVenue(data) {
  return request.post('/venue-bookings', data)
}

export function cancelBooking(id) {
  return request.delete(`/venue-bookings/${id}`)
}

export function listEquipment(params) {
  return request.get('/equipment', { params })
}

export function borrowEquipment(data) {
  return request.post('/equipment-borrows', data)
}

export function returnEquipment(id) {
  return request.put(`/equipment-borrows/${id}/return`)
}
