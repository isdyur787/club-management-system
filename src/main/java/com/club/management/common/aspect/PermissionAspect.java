package com.club.management.common.aspect;

import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.config.RedisKeyConstants;
import com.club.management.common.exception.ForbiddenException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.util.SecurityUtil;
import com.club.management.user.mapper.RoleMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 权限检查切面，先查用户与权限编码的 Redis 缓存，缓存不可用时查询角色权限关系，未授权则阻止方法执行。
 */
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_MINUTES = 10;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint point, RequirePermission requirePermission) throws Throwable {
        Long userId = SecurityUtil.getCurrentUserId();
        String permission = requirePermission.value();

        // 缓存键包含账号和具体权限；角色能登录，并不代表它拥有每一个业务操作权限。
        String cacheKey = RedisKeyConstants.PERMISSION_CACHE + userId + ":" + permission;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if ("1".equals(cached)) {
                    return point.proceed();
                } else {
                    throw new ForbiddenException(ErrorCode.FORBIDDEN);
                }
            }
        } catch (ForbiddenException e) { throw e; }
          catch (Exception e) { /* Redis 不可用，降级为 DB */ }

        boolean hasPerm = roleMapper.countUserPermission(userId, permission) > 0;

        // 允许和拒绝结果都缓存 10 分钟；权限变更后需考虑这段缓存有效期。
        try { redisTemplate.opsForValue().set(cacheKey, hasPerm ? "1" : "0", CACHE_TTL_MINUTES, TimeUnit.MINUTES); } catch (Exception ignored) {}

        if (!hasPerm) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        return point.proceed();
    }
}
