package com.club.management.common.config;

import java.util.List;

/**
 * 安全相关常量 — 统一管理公开路径，避免 SecurityConfig 与 JwtAuthenticationFilter 重复定义
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    /** 不需要 JWT 认证的公开路径 */
    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register"
    );
}
