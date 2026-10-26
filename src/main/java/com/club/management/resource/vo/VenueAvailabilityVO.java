package com.club.management.resource.vo;

import lombok.Data;

import java.util.List;

@Data
public class VenueAvailabilityVO {

    private Long venueId;
    private String venueName;
    private String date;
    private List<BookedSlot> bookedSlots;
    private List<AvailableSlot> availableSlots;

    @Data
    public static class BookedSlot {
        private String startTime;
        private String endTime;
        private String activityTitle;
    }

    @Data
    public static class AvailableSlot {
        private String startTime;
        private String endTime;
    }
}
