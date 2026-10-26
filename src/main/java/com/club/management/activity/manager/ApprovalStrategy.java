package com.club.management.activity.manager;

import java.util.List;

public interface ApprovalStrategy {

    List<ApprovalLevel> getApprovalLevels();

    boolean isAutoApproved(com.club.management.activity.entity.Activity activity);

    class ApprovalLevel {
        private final int level;
        private final String description;

        public ApprovalLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
}
