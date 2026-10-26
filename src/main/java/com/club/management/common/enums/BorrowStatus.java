package com.club.management.common.enums;

/**
 * 设备借用状态
 */
public enum BorrowStatus {
    PENDING(0),
    RETURNED(1);

    private final int code;

    BorrowStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BorrowStatus fromCode(int code) {
        for (BorrowStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown BorrowStatus code: " + code);
    }
}
