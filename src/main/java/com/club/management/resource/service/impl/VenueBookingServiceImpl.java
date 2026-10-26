package com.club.management.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.exception.*;
import com.club.management.resource.dto.VenueBookingDTO;
import com.club.management.resource.entity.Venue;
import com.club.management.resource.entity.VenueBooking;
import com.club.management.resource.mapper.VenueBookingMapper;
import com.club.management.resource.mapper.VenueMapper;
import com.club.management.resource.service.VenueBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueBookingServiceImpl implements VenueBookingService {

    @Autowired
    private VenueMapper venueMapper;

    @Autowired
    private VenueBookingMapper venueBookingMapper;

    @Autowired
    private ClubMemberMapper clubMemberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bookVenue(Long userId, VenueBookingDTO dto) {
        Venue venue = venueMapper.selectById(dto.getVenueId());
        if (venue == null) {
            throw new NotFoundException(ErrorCode.VENUE_NOT_FOUND);
        }

        if (dto.getEndTime().isBefore(dto.getStartTime()) || dto.getEndTime().equals(dto.getStartTime())) {
            throw new ValidationException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }

        // 冲突检测
        LambdaQueryWrapper<VenueBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VenueBooking::getVenueId, dto.getVenueId())
                .eq(VenueBooking::getBookingDate, dto.getBookingDate())
                .eq(VenueBooking::getStatus, ActiveStatus.ACTIVE.getCode())
                .apply("start_time < ? AND end_time > ?", dto.getEndTime(), dto.getStartTime());
        if (venueBookingMapper.selectCount(wrapper) > 0) {
            throw new ConflictException(ErrorCode.VENUE_TIME_CONFLICT);
        }

        Long clubId = clubMemberMapper.getClubIdByUserId(userId);
        if (clubId == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        VenueBooking booking = new VenueBooking();
        booking.setVenueId(dto.getVenueId());
        booking.setActivityId(dto.getActivityId());
        booking.setClubId(clubId);
        booking.setBookingDate(dto.getBookingDate());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setStatus(ActiveStatus.ACTIVE.getCode());
        venueBookingMapper.insert(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBooking(Long bookingId) {
        VenueBooking booking = venueBookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new NotFoundException(ErrorCode.RESOURCE_NOT_AVAILABLE);
        }
        Long currentClubId = com.club.management.common.util.TenantContext.getClubId();
        if (currentClubId != null && !currentClubId.equals(booking.getClubId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权取消其他社团的预约");
        }
        booking.setStatus(ActiveStatus.INACTIVE.getCode());
        venueBookingMapper.updateById(booking);
    }
}
