package com.club.management.common.config;

import com.club.management.common.response.R;
import com.club.management.common.config.RedisKeyConstants;
import com.club.management.common.util.JwtUtil;
import com.club.management.common.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器，验证签名和注销黑名单，把用户与角色写入本次请求上下文，并在过滤链结束后清理。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SecurityConstants.PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 没有令牌时继续过滤链，是否允许访问由 SecurityConfig 的公开路径规则决定。
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            // 解析过程会验证签名和有效期；成功后才能信任令牌中的用户与角色。
            Claims claims = jwtUtil.parseToken(token);

            // 检查 Token 是否在注销黑名单中
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyConstants.JWT_BLACKLIST + token))) {
                writeErrorResponse(response);
                return;
            }

            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);

            SecurityContext.setUserId(userId);
            SecurityContext.setUsername(username);

            // 从 JWT claims 中读取真实角色列表
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = (roles != null)
                    ? roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList())
                    : Collections.emptyList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            try {
                filterChain.doFilter(request, response);
            } finally {
                // Controller、Service 和权限切面执行期间仍可读取身份。
                // 请求线程会被线程池复用，结束时移除身份，避免下一次请求读到上一位用户。
                SecurityContext.clear();
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            writeErrorResponse(response);
        }
    }

    private void writeErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(40002, "未登录或登录已过期")));
    }
}
