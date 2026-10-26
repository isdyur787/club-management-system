package com.club.management.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RegistrationFullEvent extends ApplicationEvent {

    private final Long activityId;
    private final Integer maxCount;

    public RegistrationFullEvent(Object source, Long activityId, Integer maxCount) {
        super(source);
        this.activityId = activityId;
        this.maxCount = maxCount;
    }
}
