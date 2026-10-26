package com.club.management.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityRejectedEvent extends ApplicationEvent {

    private final Long activityId;
    private final Long clubId;
    private final String reason;

    public ActivityRejectedEvent(Object source, Long activityId, Long clubId, String reason) {
        super(source);
        this.activityId = activityId;
        this.clubId = clubId;
        this.reason = reason;
    }
}
