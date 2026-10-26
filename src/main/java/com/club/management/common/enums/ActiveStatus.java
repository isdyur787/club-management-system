package com.club.management.common.enums;

/**
 * 通用启用/停用状态，适用于成员、报名、预约等场景
 */
public enum ActiveStatus {
    ACTIVE(1),
    INACTIVE(0);

    private final int code;

    ActiveStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ActiveStatus fromCode(int code) {
        for (ActiveStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ActiveStatus code: " + code);
    }
}
