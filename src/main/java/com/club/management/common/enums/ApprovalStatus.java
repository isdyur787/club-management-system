package com.club.management.common.enums;

public enum ApprovalStatus {
    PENDING(0),
    APPROVED(1),
    REJECTED(2);

    private final int code;

    ApprovalStatus(int code) {
        this.code = code;
    }

    public int getCode() { return code; }

    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalStatus code: " + code);
    }
}
