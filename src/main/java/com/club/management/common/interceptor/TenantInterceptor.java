package com.club.management.common.interceptor;

import com.club.management.common.util.SecurityContext;
import com.club.management.common.util.TenantContext;
import com.club.management.club.mapper.ClubMemberMapper;
import com.club.management.user.mapper.RoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 社团租户上下文拦截器，根据登录账号查询管理员身份或默认成员社团，并在请求完成后清除 ThreadLocal。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private ClubMemberMapper clubMemberMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_MINUTES = 5;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return true;
        }

        // 尝试从 Redis 缓存获取
        String cacheKey = "tenant:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if ("ADMIN".equals(cached)) {
                    TenantContext.setClubId(null);
                } else if (cached.isEmpty()) {
                    TenantContext.setClubId(null);
                } else {
                    TenantContext.setClubId(Long.parseLong(cached));
                }
                return true;
            }
        } catch (Exception e) {
            // Redis 不可用时降级为 DB 查询
        }

        // 管理员不设置 clubId；MyBatis 租户插件遇到空上下文时不追加社团条件。
        if (roleMapper.countAdminRole(userId) > 0) {
            TenantContext.setClubId(null);
            try { redisTemplate.opsForValue().set(cacheKey, "ADMIN", CACHE_TTL_MINUTES, TimeUnit.MINUTES); } catch (Exception ignored) {}
            return true;
        }

        // 普通账号取一个有效成员社团作为默认值，业务需要切换社团时还须另行校验访问资格。
        Long clubId = clubMemberMapper.getClubIdByUserId(userId);
        if (clubId != null) {
            TenantContext.setClubId(clubId);
            try { redisTemplate.opsForValue().set(cacheKey, clubId.toString(), CACHE_TTL_MINUTES, TimeUnit.MINUTES); } catch (Exception ignored) {}
        } else {
            TenantContext.setClubId(null);
            try { redisTemplate.opsForValue().set(cacheKey, "", CACHE_TTL_MINUTES, TimeUnit.MINUTES); } catch (Exception ignored) {}
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        // ThreadLocal 只属于当前线程，必须 remove，避免线程复用造成社团数据串读。
        TenantContext.clear();
    }
}
