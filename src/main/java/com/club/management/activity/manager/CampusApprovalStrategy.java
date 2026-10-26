package com.club.management.activity.manager;

import com.club.management.activity.entity.Activity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CampusApprovalStrategy implements ApprovalStrategy {

    @Override
    public List<ApprovalLevel> getApprovalLevels() {
        return List.of(
                new ApprovalLevel(1, "社长确认"),
                new ApprovalLevel(2, "指导老师审批")
        );
    }

    @Override
    public boolean isAutoApproved(Activity activity) {
        return activity.getBudget().compareTo(new BigDecimal("100")) <= 0;
    }
}
