package com.club.management.activity.manager;

import com.club.management.activity.entity.Activity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LargeApprovalStrategy implements ApprovalStrategy {

    @Override
    public List<ApprovalLevel> getApprovalLevels() {
        return List.of(
                new ApprovalLevel(1, "社长确认"),
                new ApprovalLevel(2, "指导老师审批"),
                new ApprovalLevel(3, "团委审批")
        );
    }

    @Override
    public boolean isAutoApproved(Activity activity) {
        return false;
    }
}
