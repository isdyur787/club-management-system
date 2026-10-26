# 安全与权限设计文档

## 1. RBAC 权限模型

### 1.1 模型结构

```
用户 (User) ──N:M── 角色 (Role) ──N:M── 权限 (Permission)
                      │
                      └── 可关联社团 (club_id)
```

- 一个用户可以拥有多个角色
- 一个角色可以分配给多个用户
- 角色与社团关联时（如 CLUB_PRESIDENT），表示该角色仅在特定社团范围内有效
- 权限是最终鉴权的最小粒度，Controller 方法标注所需权限

### 1.2 角色定义

| 角色 | role_code | 说明 |
|---|---|---|
| 学生 | STUDENT | 默认角色，可浏览活动、报名、签到 |
| 社团负责人 | CLUB_PRESIDENT | 管理社团事务，创建活动，管理成员 |
| 指导老师 | TEACHER | 审批活动，查看社团数据 |
| 系统管理员 | ADMIN | 全部权限，管理用户和角色 |

### 1.3 权限编码规范

格式：`{module}:{action}`

| 权限编码 | 说明 |
|---|---|
| club:create | 创建社团 |
| club:edit | 编辑社团信息 |
| club:delete | 删除社团 |
| club:approve | 审批社团 |
| club:member:manage | 管理社团成员 |
| activity:create | 创建活动 |
| activity:edit | 编辑活动 |
| activity:delete | 删除活动 |
| activity:approve | 审批活动 |
| activity:view | 查看活动 |
| registration:create | 报名活动 |
| registration:manage | 管理报名记录 |
| signin:manage | 管理签到 |
| resource:manage | 管理资源 |
| resource:book | 预约资源 |
| notification:view | 查看通知 |
| notification:send | 发送通知 |
| statistics:view | 查看统计 |
| system:user:manage | 用户管理 |
| system:role:manage | 角色管理 |
| system:log:view | 查看日志 |

---

## 2. JWT 认证流程

### 2.1 认证流程

```
1. 用户提交 username + password → POST /api/v1/auth/login
2. 服务端验证密码（BCrypt）
3. 验证通过 → 生成 JWT Token
4. 返回 Token 给前端
5. 前端存储 Token（localStorage 或 sessionStorage）
6. 后续请求在 Header 中携带：Authorization: Bearer <token>
7. 服务端拦截器解析 Token，提取 userId
8. 将 userId 存入 SecurityContext（ThreadLocal）
9. 业务代码通过 SecurityUtil.getCurrentUserId() 获取当前用户
```

### 2.2 JWT Token 内容

```json
{
    "sub": "1",                    // userId
    "username": "zhangsan",
    "roles": ["STUDENT"],
    "iat": 1719000000,             // 签发时间
    "exp": 1719086400              // 过期时间（24小时后）
}
```

### 2.3 Spring Security 配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                    // 前后端分离不需要 CSRF
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers("/api/v1/ai/**").permitAll()  // AI 接口预留
                .requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 2.4 JWT 认证过滤器

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);

            // 设置安全上下文
            SecurityContext.setUserId(userId);
            SecurityContext.setUsername(username);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Token 无效或过期
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40002,\"message\":\"未登录或登录已过期\"}");
        } finally {
            SecurityContext.clear();
        }
    }
}
```

### 2.5 SecurityContext（ThreadLocal）

```java
public class SecurityContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static void setUsername(String username) { USERNAME.set(username); }
    public static String getUsername() { return USERNAME.get(); }
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}

// 工具类
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
```

---

## 3. 权限校验设计

### 3.1 @RequirePermission 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();  // 权限编码，如 "activity:create"
}
```

### 3.2 权限校验切面

```java
@Aspect
@Component
public class PermissionAspect {

    @Autowired private UserService userService;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint point,
                                   RequirePermission requirePermission) throws Throwable {
        Long userId = SecurityUtil.getCurrentUserId();
        String permission = requirePermission.value();

        // 查询用户是否拥有该权限
        boolean hasPermission = userService.hasPermission(userId, permission);
        if (!hasPermission) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        return point.proceed();
    }
}
```

### 3.3 使用示例

```java
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    @PostMapping
    @RequirePermission("activity:create")
    @OperationLog(module = "活动管理", operation = "创建活动")
    public R<ActivityVO> createActivity(@Valid @RequestBody ActivityCreateDTO dto) {
        // ...
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("activity:approve")
    @OperationLog(module = "活动管理", operation = "审批活动")
    public R<Void> approveActivity(@PathVariable Long id,
                                    @Valid @RequestBody ApprovalDTO dto) {
        // ...
    }
}
```

### 3.4 业务级权限校验

对于"只能操作自己创建的资源"这类业务权限，在 Service 层手动校验：

```java
// ActivityServiceImpl.java
public void updateActivity(Long id, ActivityUpdateDTO dto) {
    Activity activity = activityMapper.selectById(id);
    if (activity == null) {
        throw new NotFoundException(ErrorCode.ACTIVITY_NOT_FOUND);
    }

    Long currentUserId = SecurityUtil.getCurrentUserId();
    // 业务权限校验：只有创建者或社团负责人可以修改
    if (!activity.getCreatedBy().equals(currentUserId)
            && !clubMemberService.isPresident(activity.getClubId(), currentUserId)) {
        throw new ForbiddenException(ErrorCode.FORBIDDEN);
    }

    // 继续业务逻辑...
}
```

---

## 4. 数据隔离（多租户）

### 4.1 隔离策略

- 所有业务表包含 `club_id` 字段
- MyBatis-Plus TenantLineInnerInterceptor 自动在 SQL 中追加 `WHERE club_id = ?`
- club_id 从 TenantContext（ThreadLocal）获取，由 TenantInterceptor 在请求进入时设置

### 4.2 隔离规则

| 表类型 | 是否隔离 | 说明 |
|---|---|---|
| sys_* | 否 | 系统表，全局共享 |
| club_info | 否 | 社团本身是跨社团可见的 |
| club_member | 是 | 成员数据按社团隔离 |
| act_activity | 是 | 活动按社团隔离 |
| act_approval | 是 | 审批记录按社团隔离 |
| reg_registration | 是 | 报名数据按社团隔离 |
| reg_sign_in | 是 | 签到数据按社团隔离 |
| res_venue | 否 | 场地是公共资源 |
| res_venue_booking | 是 | 预约记录按社团隔离 |
| res_equipment | 否 | 设备是公共资源 |
| res_equipment_borrow | 是 | 借用记录按社团隔离 |
| noti_notification | 否 | 通知按用户维度，不按社团 |
| operation_log | 否 | 日志全局可查 |

### 4.3 管理员绕过

系统管理员（role_code = ADMIN）绕过租户过滤：

```java
// TenantInterceptor.java
@Override
public boolean preHandle(...) {
    Long userId = SecurityUtil.getCurrentUserId();
    // 管理员不做租户隔离
    if (userService.isAdmin(userId)) {
        TenantContext.setClubId(null);  // null 时插件不追加条件
        return true;
    }
    Long clubId = clubMemberMapper.getClubIdByUserId(userId);
    TenantContext.setClubId(clubId);
    return true;
}
```

---

## 5. 密码安全

### 5.1 存储

- 使用 BCrypt 加密，强度因子默认 10
- 不存储明文密码，不存储可逆加密的密码

### 5.2 密码规则

前端 + 后端双重校验：
- 长度 8-20 字符
- 必须包含大写字母、小写字母、数字
- 不能包含用户名

### 5.3 登录安全

- 连续 5 次密码错误 → 锁定账号 15 分钟
- 锁定状态存储在 Redis：`login_fail:{username}` → 失败次数，TTL 15 分钟
- 登录成功后清除失败计数

---

## 6. 输入安全

### 6.1 XSS 防护

- 所有字符串输入在 Controller 层通过 `@Valid` 校验长度
- 输出到前端时由 Vue 自动转义（Vue 模板默认转义）
- 富文本内容（如活动描述）使用白名单过滤（后续如需支持）

### 6.2 SQL 注入防护

- MyBatis-Plus 参数化查询，不使用 `${}` 拼接
- 自定义查询使用 `#{}` 占位符

### 6.3 文件上传安全（后续扩展）

- 限制文件类型（白名单）
- 限制文件大小（默认 10MB）
- 文件名重命名（UUID），不使用原始文件名
- 存储路径不暴露给用户直接访问

---

## 7. 限流设计

### 7.1 限流场景

| 接口 | 限流策略 | 说明 |
|---|---|---|
| 签到接口 | 每用户每秒 1 次 | 活动开始时集中访问 |
| 登录接口 | 每 IP 每分钟 10 次 | 防暴力破解 |
| 报名接口 | 每用户每秒 1 次 | 防重复提交（配合幂等注解） |

### 7.2 Redis + Lua 滑动窗口实现

```lua
-- rate_limit.lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])        -- 最大请求数
local window = tonumber(ARGV[2])       -- 时间窗口（毫秒）
local now = tonumber(ARGV[3])          -- 当前时间戳

-- 移除窗口外的请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 当前窗口内的请求数
local count = redis.call('ZCARD', key)
if count >= limit then
    return 0  -- 拒绝
end

-- 添加当前请求
redis.call('ZADD', key, now, now .. math.random(1000000))
redis.call('PEXPIRE', key, window)
return 1  -- 通过
```

### 7.3 限流注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int limit() default 10;           // 最大请求数
    int window() default 60000;       // 时间窗口（毫秒），默认 1 分钟
    String key() default "";          // 限流 key 的 SpEL 表达式
}
```

---

## 8. 安全配置汇总

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
  expiration: 86400000  # 24小时

security:
  login:
    max-fail-count: 5
    lock-minutes: 15
  rate-limit:
    sign-in:
      limit: 1
      window: 1000
    login:
      limit: 10
      window: 60000
  password:
    min-length: 8
    max-length: 20
```
