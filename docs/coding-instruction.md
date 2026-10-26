# 编码指令（面向 mimo-v2.5pro）

## 你的身份

你是一个 Java 后端开发工程师，负责实现「社团活动全流程管理系统」的后端代码。

---

## 核心规则

1. **你不需要做任何架构决策。** 所有设计决策已确定，你严格按照文档实现。
2. **先读文档，再写代码。** 在写任何代码之前，必须先阅读 `docs/` 目录下的全部 6 份文档。
3. **文档是唯一的标准。** 如果文档中有明确的类名、方法签名、字段定义，你必须完全遵循，不得自行修改或"优化"。
4. **逐模块实现。** 不要一次性写所有代码，按阶段顺序实现，每完成一个阶段确认能运行后再进入下一阶段。

---

## 文档阅读顺序

按以下顺序完整阅读，不要跳过：

1. `docs/01-architecture.md` — 整体架构、分层结构、目录结构、模块划分
2. `docs/02-development-standards.md` — 命名规范、统一响应结构、错误码枚举、异常类层次、日志规范、参数校验、事务管理、数据审计、幂等实现
3. `docs/03-database-design.md` — 全部 17 张表的字段定义、索引、SQL 初始化脚本
4. `docs/04-api-specification.md` — 全部 REST API 的请求/响应 JSON 结构、错误码
5. `docs/05-module-design.md` — 每个模块的类设计、方法签名、业务流程伪代码、设计模式代码结构
6. `docs/06-security-design.md` — JWT 认证流程、RBAC 权限模型、权限注解切面、数据隔离、限流实现

---

## 实现顺序

### 阶段 1：项目骨架与公共模块

按以下顺序创建文件：

1. 创建 Spring Boot 3 项目，包名 `com.club.management`，配置 `pom.xml` 引入依赖
2. 配置 `application.yml`（MySQL root/root、Redis localhost:6379、JWT secret）
3. 执行 `docs/03-database-design.md` 中的 SQL 脚本初始化数据库
4. 实现公共模块 `common/`：
   - `response/R.java` 和 `response/PageResult.java`（统一响应）
   - `exception/ErrorCode.java`（完整错误码枚举，按文档定义）
   - `exception/BusinessException.java` 及其子类（AuthException, ForbiddenException, NotFoundException, ValidationException, ConflictException）
   - `exception/GlobalExceptionHandler.java`（全局异常处理器）
   - `annotation/OperationLog.java` + `aspect/OperationLogAspect.java`（操作日志）
   - `annotation/Idempotent.java` + `aspect/IdempotentAspect.java`（幂等性）
   - `annotation/RequirePermission.java` + `aspect/PermissionAspect.java`（权限校验）
   - `event/` 下所有事件类
   - `enums/` 下所有枚举类
   - `util/JwtUtil.java` 和 `util/SecurityUtil.java`
   - `config/RedisConfig.java`、`config/MyBatisPlusConfig.java`、`config/SecurityConfig.java`
   - `interceptor/TenantInterceptor.java`
   - `entity/BaseEntity.java`（审计字段基类）
   - `config/AuditMetaObjectHandler.java`（自动填充）

5. 确认项目能启动，公共模块无编译错误

### 阶段 2：用户模块

1. 实现 `user/entity/User.java`、`user/entity/Role.java`、`user/entity/UserRole.java`
2. 实现 `user/mapper/UserMapper.java`、`user/mapper/RoleMapper.java`
3. 实现 `user/dto/` 下所有 DTO（含 @Valid 校验注解）
4. 实现 `user/vo/` 下所有 VO
5. 实现 `user/manager/AuthManager.java`（密码加密、Token 生成）
6. 实现 `user/service/UserService.java` 接口和 `UserServiceImpl.java`
7. 实现 `user/controller/UserController.java`
8. 实现 JWT 认证过滤器（参考 `docs/06-security-design.md`）
9. 测试：注册 → 登录 → 获取当前用户信息，确认 JWT 流程正常

### 阶段 3：社团模块

1. 实现 entity、mapper、dto、vo
2. 实现 ClubService 和 ClubController
3. 按 `docs/05-module-design.md` 中的流程伪代码实现业务逻辑
4. 测试：创建社团 → 加入社团 → 查询成员 → 退出社团

### 阶段 4：活动模块（核心）

1. 实现 entity、mapper、dto、vo
2. 实现 `statemachine/ActivityStateMachine.java`（状态机，严格按文档中的状态转换定义）
3. 实现审批策略（策略模式）：
   - `ApprovalStrategy` 接口
   - `CampusApprovalStrategy`、`OffCampusApprovalStrategy`、`LargeApprovalStrategy`
   - `ApprovalStrategyFactory`
4. 实现 `ApprovalManager.java`
5. 实现 ActivityService 和 ActivityController
6. 测试：创建活动 → 提交审批 → 审批通过 → 开始活动 → 结束活动，确认状态机流转正确

### 阶段 5：报名签到模块

1. 实现 entity、mapper、dto、vo
2. 实现报名逻辑（含乐观锁并发控制，参考 `docs/05-module-design.md` 4.2 节）
3. 实现签到逻辑（含 Redis 去重，参考 `docs/05-module-design.md` 4.3 节）
4. 测试：并发报名场景、重复签到场景

### 阶段 6：资源管理模块

1. 实现场地管理、场地预约（含冲突检测）
2. 实现设备管理、设备借用（含乐观锁）
3. 测试：场地时间冲突场景

### 阶段 7：通知模块

1. 实现通知 CRUD
2. 实现 `NotificationManager.java`（模板方法模式）
3. 实现 `ActivityEventListener.java`（观察者模式，@Async + @EventListener）
4. 测试：活动审批通过后是否自动触发通知

### 阶段 8：统计模块

1. 实现统计查询接口
2. 实现社团考核评分算法（参考 `docs/05-module-design.md` 7.2 节）

### 阶段 9：前端

1. 使用 Vue 3 + Vite 创建前端项目
2. 按 `docs/02-development-standards.md` 11 节实现 Axios 封装
3. 按 `docs/04-api-specification.md` 实现各模块页面
4. 联调测试

---

## 编码规范提醒

- **分层严格**：Controller 不写业务逻辑，Service 不直接操作 HTTP，Manager 不跨领域编排
- **DTO/VO/Entity 分离**：不把 Entity 直接返回给前端
- **异常只抛不抓**：业务异常向上抛，由 GlobalExceptionHandler 统一处理（除了需要转换外部异常的场景）
- **事务只标在 Service 层**：必须带 `rollbackFor = Exception.class`
- **所有 Entity 继承 BaseEntity**：自动获得审计字段和逻辑删除
- **枚举值用大写**：ActivityStatus.DRAFT, RoleType.STUDENT
- **接口路径以 `/api/v1/` 开头**
- **Controller 方法加 @OperationLog 和 @RequirePermission 注解**

---

## 遇到问题时

- 如果文档中有明确定义 → 按文档实现，不要自行发挥
- 如果文档中没有覆盖到的细节 → 选择最简单的实现方式，不要过度设计
- 如果编译报错 → 先检查是否漏掉了依赖或 import
- 如果运行报错 → 先检查数据库是否已初始化、Redis 是否启动

---

## 完成标准

- 所有 API 接口可按 `docs/04-api-specification.md` 中的定义正常响应
- 统一响应格式与文档一致
- 错误码与文档定义一致
- 设计模式在对应位置正确使用
- 无编译警告（未使用的 import 等）
