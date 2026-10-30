package com.club.management.common.util;

/**
 * 当前请求线程的社团编号容器，供数据库租户插件读取；普通 ThreadLocal 不会自动传播到异步线程。
 */
public class TenantContext {

    private static final ThreadLocal<Long> CLUB_ID = new ThreadLocal<>();

    public static void setClubId(Long clubId) { CLUB_ID.set(clubId); }
    public static Long getClubId() { return CLUB_ID.get(); }
    public static void clear() { CLUB_ID.remove(); }
}
