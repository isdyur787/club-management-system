package com.club.management.common.util;

public class TenantContext {

    private static final ThreadLocal<Long> CLUB_ID = new ThreadLocal<>();

    public static void setClubId(Long clubId) { CLUB_ID.set(clubId); }
    public static Long getClubId() { return CLUB_ID.get(); }
    public static void clear() { CLUB_ID.remove(); }
}
