package com.club.management.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityCompletedEvent extends ApplicationEvent {

    private final Long activityId;
    private final Long clubId;
    private final Integer participantCount;

    public ActivityCompletedEvent(Object source, Long activityId, Long clubId, Integer participantCount) {
        super(source);
        this.activityId = activityId;
        this.clubId = clubId;
        this.participantCount = participantCount;
    }
}
