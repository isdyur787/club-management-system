package com.club.management.common.enums;

/**
 * 社团审批状态
 */
public enum ClubApprovalStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final int code;

    ClubApprovalStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ClubApprovalStatus fromCode(int code) {
        for (ClubApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ClubApprovalStatus code: " + code);
    }
}
