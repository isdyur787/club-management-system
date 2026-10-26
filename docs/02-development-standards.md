# 开发规范文档

## 1. 命名规范

### 1.1 包名

全小写，按模块划分：
```
com.club.management.{module}.{layer}
```

示例：
```
com.club.management.activity.controller
com.club.management.activity.service.impl
com.club.management.activity.manager
com.club.management.activity.mapper
com.club.management.activity.entity
com.club.management.activity.dto
com.club.management.activity.vo
```

### 1.2 类名

| 类型 | 命名规则 | 示例 |
|---|---|---|
| Controller | `{业务名}Controller` | `ActivityController` |
| Service 接口 | `{业务名}Service` | `ActivityService` |
| Service 实现 | `{业务名}ServiceImpl` | `ActivityServiceImpl` |
| Manager | `{业务名}Manager` | `ApprovalManager` |
| Mapper | `{业务名}Mapper` | `ActivityMapper` |
| Entity | `{业务名}` | `Activity` |
| DTO | `{业务名}{操作}DTO` | `ActivityCreateDTO`, `ActivityQueryDTO` |
| VO | `{业务名}VO` | `ActivityVO`, `ActivityDetailVO` |
| 枚举 | `{业务名}Enum` 或描述性名称 | `ActivityStatus`, `RoleType` |
| 异常 | `{描述}Exception` | `BusinessException`, `AuthException` |
| 配置类 | `{描述}Config` | `RedisConfig`, `SecurityConfig` |
| 切面 | `{描述}Aspect` | `OperationLogAspect` |
| 注解 | 描述性名称 | `@OperationLog`, `@Idempotent` |
| 事件 | `{业务}{动作}Event` | `ActivityApprovedEvent` |
| 监听器 | `{业务}Listener` | `ActivityEventListener` |
| 工具类 | `{描述}Util` | `JwtUtil`, `SecurityUtil` |

### 1.3 方法名

| 层 | 命名规则 | 示例 |
|---|---|---|
| Controller | `{动作}{业务名}` | `createActivity()`, `listActivities()` |
| Service | 同 Controller | `createActivity()`, `listActivities()` |
| Manager | `{描述性动词}` | `checkApprovalEligibility()`, `calculateScore()` |
| Mapper | MyBatis-Plus 自带 / `{select/insert/update/delete}{Entity}` | `selectByClubId()` |

### 1.4 变量名

- 驼峰命名
- 布尔变量以 `is` / `has` / `can` 开头：`isActive`, `hasPermission`
- 常量全大写下划线分隔：`MAX_RETRY_COUNT`

### 1.5 数据库命名

| 类型 | 规则 | 示例 |
|---|---|---|
| 表名 | 小写下划线，模块前缀 | `sys_user`, `club_info`, `act_activity` |
| 字段名 | 小写下划线 | `user_name`, `created_at` |
| 索引名 | `idx_{表名}_{字段名}` | `idx_activity_club_id` |
| 唯一索引 | `uk_{表名}_{字段名}` | `uk_registration_user_activity` |

**表名前缀：**

| 前缀 | 模块 |
|---|---|
| `sys_` | 系统（用户、角色、权限） |
| `club_` | 社团 |
| `act_` | 活动 |
| `reg_` | 报名签到 |
| `res_` | 资源 |
| `noti_` | 通知 |
| `stat_` | 统计 |

---

## 2. 统一响应结构

### 2.1 响应类定义

```java
package com.club.management.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    private Integer code;       // 业务状态码
    private String message;     // 提示信息
    private T data;             // 响应数据
    private Long timestamp;     // 时间戳（毫秒）

    // 成功（无数据）
    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // 成功（有数据）
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // 失败
    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // 失败（从 ErrorCode 枚举构建）
    public static <T> R<T> fail(ErrorCode errorCode) {
        R<T> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage());
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // 失败（从 ErrorCode 枚举构建，附加详细信息）
    public static <T> R<T> fail(ErrorCode errorCode, String detail) {
        R<T> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage() + ": " + detail);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // getter/setter 省略
}
```

### 2.2 分页响应类定义

```java
package com.club.management.common.response;

import java.util.List;

public class PageResult<T> {
    private Integer pageNum;     // 当前页码
    private Integer pageSize;    // 每页条数
    private Long total;          // 总记录数
    private List<T> list;        // 当前页数据

    // 构造方法和 getter/setter 省略
}
```

### 2.3 响应示例

**成功（有数据）：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "name": "编程社"
    },
    "timestamp": 1719000000000
}
```

**成功（分页）：**
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "pageNum": 1,
        "pageSize": 10,
        "total": 50,
        "list": [...]
    },
    "timestamp": 1719000000000
}
```

**失败：**
```json
{
    "code": 40001,
    "message": "活动人数已满",
    "timestamp": 1719000000000
}
```

---

## 3. 错误码定义

### 3.1 错误码规则

错误码为 5 位整数，按模块分段：

| 范围 | 模块 |
|---|---|
| 200 | 成功 |
| 40000-40099 | 通用错误（参数校验、认证、权限） |
| 40100-40199 | 用户模块 |
| 40200-40299 | 社团模块 |
| 40300-40399 | 活动模块 |
| 40400-40499 | 报名签到模块 |
| 40500-40599 | 资源模块 |
| 40600-40699 | 通知模块 |
| 40700-40799 | 统计模块 |
| 42900 | 限流 |
| 50000-50099 | 系统内部错误 |

### 3.2 错误码枚举类

```java
package com.club.management.common.exception;

public enum ErrorCode {

    // ========== 通用错误 ==========
    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40002, "未登录或登录已过期"),
    FORBIDDEN(40003, "无权限执行此操作"),
    RESOURCE_NOT_FOUND(40004, "资源不存在"),
    IDEMPOTENT_CONFLICT(40005, "请勿重复提交"),
    RATE_LIMIT_EXCEEDED(42900, "请求过于频繁，请稍后重试"),

    // ========== 用户模块 ==========
    USER_NOT_FOUND(40101, "用户不存在"),
    USER_ALREADY_EXISTS(40102, "用户已存在"),
    PASSWORD_INCORRECT(40103, "密码错误"),
    ROLE_NOT_FOUND(40104, "角色不存在"),

    // ========== 社团模块 ==========
    CLUB_NOT_FOUND(40201, "社团不存在"),
    CLUB_ALREADY_EXISTS(40202, "社团名称已存在"),
    CLUB_NOT_APPROVED(40203, "社团尚未通过审批"),
    MEMBER_ALREADY_EXISTS(40204, "该用户已是社团成员"),
    MEMBER_NOT_FOUND(40205, "成员不存在"),
    CLUB_PRESIDENT_CANNOT_LEAVE(40206, "社长不能直接退出社团"),

    // ========== 活动模块 ==========
    ACTIVITY_NOT_FOUND(40301, "活动不存在"),
    ACTIVITY_STATUS_INVALID(40302, "活动状态不允许此操作"),
    ACTIVITY_APPROVAL_REJECTED(40303, "活动审批被驳回"),
    ACTIVITY_ALREADY_APPROVED(40304, "活动已审批，不可重复操作"),
    ACTIVITY_BUDGET_EXCEEDED(40305, "预算超出限制"),
    ACTIVITY_TIME_CONFLICT(40306, "活动时间与已有活动冲突"),

    // ========== 报名签到模块 ==========
    REGISTRATION_FULL(40401, "活动报名人数已满"),
    REGISTRATION_DUPLICATE(40402, "请勿重复报名"),
    REGISTRATION_CLOSED(40403, "报名已截止"),
    REGISTRATION_NOT_FOUND(40404, "报名记录不存在"),
    SIGN_IN_DUPLICATE(40405, "请勿重复签到"),
    SIGN_IN_CODE_INVALID(40406, "签到码无效"),
    SIGN_IN_TIME_EXPIRED(40407, "签到时间已过"),

    // ========== 资源模块 ==========
    RESOURCE_NOT_AVAILABLE(40501, "资源不可用"),
    VENUE_TIME_CONFLICT(40502, "场地在该时段已被预约"),
    VENUE_NOT_FOUND(40503, "场地不存在"),
    EQUIPMENT_NOT_AVAILABLE(40504, "设备已被借出"),

    // ========== 通知模块 ==========
    NOTIFICATION_NOT_FOUND(40601, "通知不存在"),

    // ========== 系统内部错误 ==========
    SYSTEM_ERROR(50001, "系统内部错误"),
    DATABASE_ERROR(50002, "数据库操作失败"),
    REDIS_ERROR(50003, "缓存操作失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}
```

---

## 4. 异常处理规范

### 4.1 异常类层次

```
RuntimeException
    └── BusinessException (抽象基类，携带 ErrorCode)
            ├── AuthException          (认证失败，HTTP 401)
            ├── ForbiddenException     (权限不足，HTTP 403)
            ├── NotFoundException      (资源不存在，HTTP 404)
            ├── ValidationException    (参数校验失败，HTTP 400)
            └── ConflictException      (资源冲突/重复提交，HTTP 409)
```

### 4.2 异常类定义

```java
// 业务异常基类
package com.club.management.common.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}

// 认证异常 → HTTP 401
public class AuthException extends BusinessException {
    public AuthException(ErrorCode errorCode) { super(errorCode); }
}

// 权限异常 → HTTP 403
public class ForbiddenException extends BusinessException {
    public ForbiddenException(ErrorCode errorCode) { super(errorCode); }
}

// 资源不存在 → HTTP 404
public class NotFoundException extends BusinessException {
    public NotFoundException(ErrorCode errorCode) { super(errorCode); }
    public NotFoundException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}

// 参数校验失败 → HTTP 400
public class ValidationException extends BusinessException {
    public ValidationException(ErrorCode errorCode) { super(errorCode); }
    public ValidationException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}

// 资源冲突 → HTTP 409
public class ConflictException extends BusinessException {
    public ConflictException(ErrorCode errorCode) { super(errorCode); }
    public ConflictException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}
```

### 4.3 全局异常处理器

```java
package com.club.management.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 业务异常 → 返回对应 HTTP 状态码和业务错误码
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 | URI={} | code={} | message={}",
                request.getRequestURI(),
                e.getErrorCode().getCode(),
                e.getMessage());
        return R.fail(e.getErrorCode());
    }

    // 认证异常 → HTTP 401
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthException(AuthException e, HttpServletRequest request) {
        log.warn("认证失败 | URI={} | message={}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getErrorCode());
    }

    // 权限异常 → HTTP 403
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        log.warn("权限不足 | URI={} | userId={} | message={}",
                request.getRequestURI(),
                SecurityUtil.getCurrentUserId(),
                e.getMessage());
        return R.fail(e.getErrorCode());
    }

    // 参数校验异常（@Valid 触发）→ HTTP 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | message={}", message);
        return R.fail(ErrorCode.PARAM_INVALID, message);
    }

    // 绑定异常 → HTTP 400
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 | message={}", message);
        return R.fail(ErrorCode.PARAM_INVALID, message);
    }

    // 兜底：未知异常 → HTTP 500
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 | URI={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
```

### 4.4 异常使用规范

| 场景 | 做法 |
|---|---|
| 业务逻辑校验失败 | 抛 `BusinessException` + 对应 ErrorCode |
| 资源不存在 | 抛 `NotFoundException` |
| 参数校验失败 | 用 `@Valid` 注解，由全局处理器捕获 |
| 重复提交 | 抛 `ConflictException(ErrorCode.IDEMPOTENT_CONFLICT)` |
| 未知异常 | 由兜底处理器捕获，返回通用错误 |
| Controller 层 | 不捕获异常，向上抛出 |
| Service 层 | 只捕获需要转换的异常，其他向上抛 |
| 外部调用 | 捕获后转为 `BusinessException`，不暴露原始异常信息 |

---

## 5. 日志规范

### 5.1 操作日志注解

```java
package com.club.management.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String module() default "";          // 模块名
    String operation() default "";       // 操作描述
    boolean saveRequestData() default true;   // 是否记录请求参数
    boolean saveResponseData() default false; // 是否记录响应结果
}
```

### 5.2 操作日志切面

```java
package com.club.management.common.aspect;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // 记录请求信息
        log.info("[{}] 请求开始 | module={} | operation={} | method={} | params={}",
                traceId,
                operationLog.module(),
                operationLog.operation(),
                point.getSignature().toShortString(),
                operationLog.saveRequestData() ? JSON.toJSONString(point.getArgs()) : "***");

        Object result;
        try {
            result = point.proceed();
        } catch (Exception e) {
            log.error("[{}] 请求异常 | module={} | operation={} | error={}",
                    traceId,
                    operationLog.module(),
                    operationLog.operation(),
                    e.getMessage());
            throw e;
        }

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[{}] 请求完成 | module={} | operation={} | cost={}ms | result={}",
                traceId,
                operationLog.module(),
                operationLog.operation(),
                costTime,
                operationLog.saveResponseData() ? JSON.toJSONString(result) : "***");

        // 异步持久化到数据库
        asyncSaveLog(traceId, operationLog, point.getArgs(), result, costTime);
        return result;
    }
}
```

### 5.3 日志级别使用规范

| 级别 | 使用场景 |
|---|---|
| ERROR | 系统异常、数据库连接失败、不可恢复的错误 |
| WARN | 业务异常、参数校验失败、权限不足、可恢复的错误 |
| INFO | 关键业务操作（创建、修改、删除、审批）、系统启动 |
| DEBUG | 方法入参出参、中间状态、调试信息 |

### 5.4 日志内容规范

- 使用占位符，不用字符串拼接：`log.info("userId={}", userId)` 而非 `log.info("userId=" + userId)`
- 异常日志带堆栈：`log.error("描述 | context={}", context, exception)`
- 每条日志包含 traceId，可追踪完整链路
- 敏感信息（密码、token）不记录

---

## 6. 参数校验规范

### 6.1 DTO 校验注解使用

```java
public class ActivityCreateDTO {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题不能超过100字")
    private String title;

    @NotBlank(message = "活动描述不能为空")
    @Size(max = 2000, message = "活动描述不能超过2000字")
    private String description;

    @NotNull(message = "开始时间不能为空")
    @Future(message = "开始时间必须是将来的时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限至少为1")
    @Max(value = 10000, message = "人数上限不能超过10000")
    private Integer maxParticipants;

    @NotNull(message = "预算不能为空")
    @DecimalMin(value = "0", message = "预算不能为负数")
    @DecimalMax(value = "100000", message = "预算不能超过100000元")
    private BigDecimal budget;

    @NotNull(message = "活动类型不能为空")
    private ActivityType type;
}
```

### 6.2 Controller 中使用 @Valid

```java
@PostMapping
@OperationLog(module = "活动管理", operation = "创建活动")
public R<ActivityVO> createActivity(@Valid @RequestBody ActivityCreateDTO dto) {
    ActivityVO vo = activityService.createActivity(dto);
    return R.ok(vo);
}
```

### 6.3 自定义校验（跨字段校验）

对于需要跨字段校验的场景（如 endTime > startTime），在 Service 层手动校验并抛出 `ValidationException`：

```java
if (dto.getEndTime().isBefore(dto.getStartTime())) {
    throw new ValidationException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
}
```

---

## 7. 事务管理规范

### 7.1 事务注解使用

```java
// Service 层标注事务
@Transactional(rollbackFor = Exception.class)
public ActivityVO createActivity(ActivityCreateDTO dto) {
    // 1. 创建活动记录
    // 2. 预约场地
    // 3. 发送通知
    // 任一步骤失败，全部回滚
}
```

### 7.2 事务传播行为

| 场景 | 传播行为 | 说明 |
|---|---|---|
| 默认业务操作 | `REQUIRED`（默认） | 加入当前事务，没有则新建 |
| 审计日志记录 | `REQUIRES_NEW` | 无论主事务是否回滚，日志都要保存 |
| 只读查询 | `NOT_SUPPORTED` 或不加事务 | 查询操作不开启事务 |

### 7.3 事务注意事项

- `@Transactional` 只标注在 Service 层方法上，不在 Controller 或 Manager
- 必须指定 `rollbackFor = Exception.class`
- 避免大事务：事务中不包含远程调用、文件操作
- 私有方法上的 `@Transactional` 不生效（AOP 限制）

---

## 8. 数据审计规范

### 8.1 自动填充配置

```java
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = SecurityUtil.getCurrentUserId();
        this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = SecurityUtil.getCurrentUserId();
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 8.2 Entity 基类

```java
@Data
public abstract class BaseEntity {
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;  // 0=未删除, 1=已删除
}
```

所有 Entity 继承 `BaseEntity`。

---

## 9. 幂等性实现规范

### 9.1 @Idempotent 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String key();                    // 幂等 key 的 SpEL 表达式
    int expireSeconds() default 10;  // 锁过期时间（秒）
    String message() default "请勿重复提交";
}
```

### 9.2 使用示例

```java
@PostMapping("/register")
@Idempotent(key = "#dto.activityId + ':' + #dto.userId", expireSeconds = 5)
public R<Void> register(@Valid @RequestBody RegistrationDTO dto) {
    registrationService.register(dto);
    return R.ok();
}
```

### 9.3 切面实现逻辑

1. 根据 SpEL 解析 key
2. 用 Redis `SETNX` 设置 key，过期时间为 `expireSeconds`
3. 设置成功 → 继续执行
4. 设置失败 → 抛出 `ConflictException(ErrorCode.IDEMPOTENT_CONFLICT)`
5. 方法执行成功后删除 key（可选，根据业务决定是否需要窗口期内防重）

---

## 10. 接口版本管理

- 所有接口以 `/api/v1/` 开头
- 版本升级时新增 `/api/v2/`，不修改 v1 接口
- 示例：`POST /api/v1/activities`, `GET /api/v1/clubs/{id}`

---

## 11. 前端规范

### 11.1 Axios 封装

```javascript
// src/api/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store'

const service = axios.create({
    baseURL: '/api/v1',
    timeout: 10000
})

// 请求拦截器：自动附加 JWT
service.interceptors.request.use(config => {
    const userStore = useUserStore()
    if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
})

// 响应拦截器：统一错误处理
service.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            ElMessage.error(res.message || '请求失败')
            // 401: 跳转登录
            if (res.code === 40002) {
                const userStore = useUserStore()
                userStore.logout()
                window.location.href = '/login'
            }
            return Promise.reject(new Error(res.message))
        }
        return res
    },
    error => {
        ElMessage.error(error.message || '网络错误')
        return Promise.reject(error)
    }
)

export default service
```

### 11.2 接口调用规范

```javascript
// src/api/activity.js
import request from './request'

export function createActivity(data) {
    return request({ url: '/activities', method: 'post', data })
}

export function listActivities(params) {
    return request({ url: '/activities', method: 'get', params })
}

export function getActivityDetail(id) {
    return request({ url: `/activities/${id}`, method: 'get' })
}

export function updateActivity(id, data) {
    return request({ url: `/activities/${id}`, method: 'put', data })
}

export function deleteActivity(id) {
    return request({ url: `/activities/${id}`, method: 'delete' })
}
```
