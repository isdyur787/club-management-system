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

        // 管理员不做租户隔离
        if (roleMapper.countAdminRole(userId) > 0) {
            TenantContext.setClubId(null);
            try { redisTemplate.opsForValue().set(cacheKey, "ADMIN", CACHE_TTL_MINUTES, TimeUnit.MINUTES); } catch (Exception ignored) {}
            return true;
        }

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
        TenantContext.clear();
    }
}
