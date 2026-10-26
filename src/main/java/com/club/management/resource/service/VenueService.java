package com.club.management.resource.service;

import com.club.management.common.response.PageResult;
import com.club.management.resource.dto.VenueQueryDTO;
import com.club.management.resource.vo.VenueAvailabilityVO;
import com.club.management.resource.vo.VenueVO;

import java.time.LocalDate;

public interface VenueService {
    PageResult<VenueVO> listVenues(VenueQueryDTO dto);
    VenueAvailabilityVO getAvailability(Long venueId, LocalDate date);
}
