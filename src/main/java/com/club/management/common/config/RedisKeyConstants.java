package com.club.management.common.config;

/**
 * Redis key 前缀常量，统一管理避免散落各处的魔法字符串。
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String JWT_BLACKLIST = "jwt:blacklist:";
    public static final String LOGIN_FAIL = "login_fail:";
    public static final String REGISTER_LOCK = "register_lock:";
    public static final String PERMISSION_CACHE = "perm:";
    public static final String SIGN_IN_CODE = "sign_in_code:";
    public static final String IDEMPOTENT = "idempotent:";
    public static final String SIGN_IN = "sign_in:";
}
