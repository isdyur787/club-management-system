package com.club.management.resource.service;

import com.club.management.resource.dto.VenueBookingDTO;

public interface VenueBookingService {
    void bookVenue(Long userId, VenueBookingDTO dto);
    void cancelBooking(Long bookingId);
}
