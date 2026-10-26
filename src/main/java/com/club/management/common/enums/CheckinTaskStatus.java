package com.club.management.common.enums;

/**
 * 打卡任务状态
 */
public enum CheckinTaskStatus {
    DRAFT(0),
    ACTIVE(1),
    CLOSED(2),
    COMPLETED(3);

    private final int code;

    CheckinTaskStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CheckinTaskStatus fromCode(int code) {
        for (CheckinTaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CheckinTaskStatus code: " + code);
    }
}
