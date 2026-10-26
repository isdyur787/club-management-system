package com.club.management.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MemberLeftEvent extends ApplicationEvent {

    private final Long userId;
    private final Long clubId;

    public MemberLeftEvent(Object source, Long userId, Long clubId) {
        super(source);
        this.userId = userId;
        this.clubId = clubId;
    }
}
