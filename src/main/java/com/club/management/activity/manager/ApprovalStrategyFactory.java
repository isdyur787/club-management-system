package com.club.management.activity.manager;

import com.club.management.common.enums.ActivityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApprovalStrategyFactory {

    @Autowired
    private CampusApprovalStrategy campusStrategy;

    @Autowired
    private OffCampusApprovalStrategy offCampusStrategy;

    @Autowired
    private LargeApprovalStrategy largeStrategy;

    public ApprovalStrategy getStrategy(ActivityType type) {
        return switch (type) {
            case CAMPUS -> campusStrategy;
            case OFF_CAMPUS -> offCampusStrategy;
            case LARGE -> largeStrategy;
        };
    }
}
