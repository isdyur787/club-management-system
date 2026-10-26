package com.club.management.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.club.management.activity.entity.Activity;
import com.club.management.activity.mapper.ActivityMapper;
import com.club.management.common.enums.ActiveStatus;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.exception.NotFoundException;
import com.club.management.common.response.PageResult;
import com.club.management.resource.dto.VenueQueryDTO;
import com.club.management.resource.entity.Venue;
import com.club.management.resource.entity.VenueBooking;
import com.club.management.resource.mapper.VenueBookingMapper;
import com.club.management.resource.mapper.VenueMapper;
import com.club.management.resource.service.VenueService;
import com.club.management.resource.vo.VenueAvailabilityVO;
import com.club.management.resource.vo.VenueVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VenueServiceImpl implements VenueService {

    @Autowired
    private VenueMapper venueMapper;

    @Autowired
    private VenueBookingMapper venueBookingMapper;

    @Autowired
    private ActivityMapper activityMapper;

    @Override
    public PageResult<VenueVO> listVenues(VenueQueryDTO dto) {
        LambdaQueryWrapper<Venue> wrapper = new LambdaQueryWrapper<>();
        if (dto.getStatus() != null) {
            wrapper.eq(Venue::getStatus, dto.getStatus());
        }
        if (dto.getCapacity() != null) {
            wrapper.ge(Venue::getCapacity, dto.getCapacity());
        }
        wrapper.orderByAsc(Venue::getVenueName);

        Page<Venue> page = venueMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        List<VenueVO> voList = page.getRecords().stream().map(venue -> {
            VenueVO vo = new VenueVO();
            vo.setId(venue.getId());
            vo.setVenueName(venue.getVenueName());
            vo.setLocation(venue.getLocation());
            vo.setCapacity(venue.getCapacity());
            vo.setFacilities(venue.getFacilities());
            vo.setStatus(venue.getStatus());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(dto.getPageNum(), dto.getPageSize(), page.getTotal(), voList);
    }

    @Override
    public VenueAvailabilityVO getAvailability(Long venueId, LocalDate date) {
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null) {
            throw new NotFoundException(ErrorCode.VENUE_NOT_FOUND);
        }

        LambdaQueryWrapper<VenueBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VenueBooking::getVenueId, venueId)
                .eq(VenueBooking::getBookingDate, date)
                .eq(VenueBooking::getStatus, ActiveStatus.ACTIVE.getCode());
        List<VenueBooking> bookings = venueBookingMapper.selectList(wrapper);

        // 批量查询活动信息，避免 N+1
        Set<Long> activityIds = bookings.stream()
                .map(VenueBooking::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Activity> activityMap = new HashMap<>();
        if (!activityIds.isEmpty()) {
            activityMapper.selectBatchIds(activityIds)
                    .forEach(a -> activityMap.put(a.getId(), a));
        }

        List<VenueAvailabilityVO.BookedSlot> bookedSlots = new ArrayList<>();
        for (VenueBooking booking : bookings) {
            VenueAvailabilityVO.BookedSlot slot = new VenueAvailabilityVO.BookedSlot();
            slot.setStartTime(booking.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            slot.setEndTime(booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            Activity activity = activityMap.get(booking.getActivityId());
            if (activity != null) {
                slot.setActivityTitle(activity.getTitle());
            }
            bookedSlots.add(slot);
        }

        List<VenueAvailabilityVO.AvailableSlot> availableSlots = calculateAvailableSlots(bookedSlots);

        VenueAvailabilityVO vo = new VenueAvailabilityVO();
        vo.setVenueId(venueId);
        vo.setVenueName(venue.getVenueName());
        vo.setDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setBookedSlots(bookedSlots);
        vo.setAvailableSlots(availableSlots);
        return vo;
    }

    private List<VenueAvailabilityVO.AvailableSlot> calculateAvailableSlots(
            List<VenueAvailabilityVO.BookedSlot> bookedSlots) {
        List<VenueAvailabilityVO.AvailableSlot> available = new ArrayList<>();
        LocalTime dayStart = LocalTime.of(8, 0);
        LocalTime dayEnd = LocalTime.of(22, 0);

        bookedSlots.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        LocalTime currentStart = dayStart;
        for (VenueAvailabilityVO.BookedSlot slot : bookedSlots) {
            LocalTime bookedStart = LocalTime.parse(slot.getStartTime(), DateTimeFormatter.ofPattern("HH:mm"));
            if (currentStart.isBefore(bookedStart)) {
                VenueAvailabilityVO.AvailableSlot availableSlot = new VenueAvailabilityVO.AvailableSlot();
                availableSlot.setStartTime(currentStart.format(DateTimeFormatter.ofPattern("HH:mm")));
                availableSlot.setEndTime(bookedStart.format(DateTimeFormatter.ofPattern("HH:mm")));
                available.add(availableSlot);
            }
            LocalTime bookedEnd = LocalTime.parse(slot.getEndTime(), DateTimeFormatter.ofPattern("HH:mm"));
            if (bookedEnd.isAfter(currentStart)) {
                currentStart = bookedEnd;
            }
        }

        if (currentStart.isBefore(dayEnd)) {
            VenueAvailabilityVO.AvailableSlot availableSlot = new VenueAvailabilityVO.AvailableSlot();
            availableSlot.setStartTime(currentStart.format(DateTimeFormatter.ofPattern("HH:mm")));
            availableSlot.setEndTime(dayEnd.format(DateTimeFormatter.ofPattern("HH:mm")));
            available.add(availableSlot);
        }

        return available;
    }
}
