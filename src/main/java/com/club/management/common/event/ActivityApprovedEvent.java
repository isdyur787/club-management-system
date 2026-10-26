package com.club.management.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ActivityApprovedEvent extends ApplicationEvent {

    private final Long activityId;
    private final Long clubId;
    private final Long approverId;

    public ActivityApprovedEvent(Object source, Long activityId, Long clubId, Long approverId) {
        super(source);
        this.activityId = activityId;
        this.clubId = clubId;
        this.approverId = approverId;
    }
}
