package com.club.management.resource.controller;

import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.PageResult;
import com.club.management.common.response.R;
import com.club.management.common.util.SecurityUtil;
import com.club.management.resource.dto.*;
import com.club.management.resource.service.*;
import com.club.management.resource.vo.EquipmentVO;
import com.club.management.resource.vo.VenueAvailabilityVO;
import com.club.management.resource.vo.VenueVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
public class ResourceController {

    @Autowired
    private VenueService venueService;

    @Autowired
    private VenueBookingService venueBookingService;

    @Autowired
    private EquipmentService equipmentService;

    @Autowired
    private EquipmentBorrowService equipmentBorrowService;

    @GetMapping("/api/v1/venues")
    public R<PageResult<VenueVO>> listVenues(VenueQueryDTO dto) {
        PageResult<VenueVO> result = venueService.listVenues(dto);
        return R.ok(result);
    }

    @GetMapping("/api/v1/venues/{id}/availability")
    public R<VenueAvailabilityVO> getAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        VenueAvailabilityVO vo = venueService.getAvailability(id, date);
        return R.ok(vo);
    }

    @PostMapping("/api/v1/venue-bookings")
    @RequirePermission("resource:book")
    @OperationLog(module = "资源管理", operation = "预约场地")
    public R<Void> bookVenue(@Valid @RequestBody VenueBookingDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        venueBookingService.bookVenue(userId, dto);
        return R.ok();
    }

    @DeleteMapping("/api/v1/venue-bookings/{id}")
    @RequirePermission("resource:book")
    @OperationLog(module = "资源管理", operation = "取消场地预约")
    public R<Void> cancelBooking(@PathVariable Long id) {
        venueBookingService.cancelBooking(id);
        return R.ok();
    }

    @GetMapping("/api/v1/equipment")
    public R<PageResult<EquipmentVO>> listEquipment(EquipmentQueryDTO dto) {
        PageResult<EquipmentVO> result = equipmentService.listEquipment(dto);
        return R.ok(result);
    }

    @PostMapping("/api/v1/equipment-borrows")
    @RequirePermission("resource:book")
    @OperationLog(module = "资源管理", operation = "借用设备")
    public R<Void> borrowEquipment(@Valid @RequestBody BorrowDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        equipmentBorrowService.borrowEquipment(userId, dto);
        return R.ok();
    }

    @PutMapping("/api/v1/equipment-borrows/{id}/return")
    @RequirePermission("resource:book")
    @OperationLog(module = "资源管理", operation = "归还设备")
    public R<Void> returnEquipment(@PathVariable Long id) {
        equipmentBorrowService.returnEquipment(id);
        return R.ok();
    }
}
