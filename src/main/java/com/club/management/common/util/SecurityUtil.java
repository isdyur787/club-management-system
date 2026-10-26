package com.club.management.common.util;

import com.club.management.common.exception.AuthException;
import com.club.management.common.exception.ErrorCode;

public class SecurityUtil {

    public static Long getCurrentUserId() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            throw new AuthException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static String getCurrentUsername() {
        return SecurityContext.getUsername();
    }
}
