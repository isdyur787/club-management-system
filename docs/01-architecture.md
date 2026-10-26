# 项目架构文档

## 1. 项目概述

**项目名称：** 社团活动全流程管理系统（Club Activity Management System）  
**包名：** `com.club.management`  
**项目结构：** 前后端分离  
**后端框架：** Spring Boot 3.x  
**前端框架：** Vue 3 + Element Plus  

---

## 2. 技术选型

| 层 | 技术 | 版本 | 说明 |
|---|---|---|---|
| 后端框架 | Spring Boot | 3.2+ | 主框架 |
| ORM | MyBatis-Plus | 3.5+ | 数据访问层 |
| 数据库 | MySQL | 8.0+ | 关系型数据库 |
| 缓存 | Redis | 7.0+ | 缓存、分布式锁、限流 |
| 认证授权 | Spring Security + JWT | - | 安全框架 |
| 接口文档 | SpringDoc OpenAPI | 2.x | 自动生成 API 文档 |
| 日志 | SLF4J + Logback | - | 日志框架 |
| 参数校验 | Jakarta Validation | - | 入参校验 |
| 前端框架 | Vue 3 | 3.4+ | 前端主框架 |
| UI 组件库 | Element Plus | 2.x | 前端 UI |
| 前端构建 | Vite | 5.x | 前端构建工具 |
| HTTP 客户端 | Axios | 1.x | 前端请求库 |
| 状态管理 | Pinia | 2.x | 前端状态管理 |
| 路由 | Vue Router | 4.x | 前端路由 |

---

## 3. 系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────┐
│              Vue 3 前端 (Vite Dev Server)         │
│              端口: 5173                           │
└──────────────────────┬──────────────────────────┘
                       │ HTTP / REST API
                       │ Content-Type: application/json
                       │ Authorization: Bearer <JWT>
┌──────────────────────▼──────────────────────────┐
│              Spring Boot 后端                     │
│              端口: 8080                           │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │           基础设施层 (Infrastructure)        │  │
│  │  全局异常处理 | AOP日志 | 参数校验 | 限流    │  │
│  └────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────┐  │
│  │           Controller 层                     │  │
│  │  接收请求 | 参数校验 | 权限检查 | 调用Service│  │
│  └────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────┐  │
│  │           Service 层                        │  │
│  │  业务编排 | 事务管理 | 事件发布              │  │
│  └────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────┐  │
│  │           Manager 层                        │  │
│  │  单一业务领域核心逻辑 | 设计模式应用         │  │
│  └────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────┐  │
│  │           DAO 层                            │  │
│  │  MyBatis-Plus Mapper | 数据访问             │  │
│  └────────────────────────────────────────────┘  │
└──────────┬───────────────────────┬──────────────┘
           │                       │
    ┌──────▼──────┐         ┌──────▼──────┐
    │   MySQL 8   │         │    Redis     │
    │  root/root  │         │  localhost   │
    │  端口: 3306 │         │  端口: 6379  │
    └─────────────┘         └─────────────┘
```

### 3.2 分层职责

| 层 | 职责 | 不允许做的事 |
|---|---|---|
| Controller | 接收 HTTP 请求、参数校验（@Valid）、调用 Service、返回统一响应 | 不写业务逻辑、不直接操作数据库 |
| Service | 业务编排（组合多个 Manager 调用）、事务管理（@Transactional）、发布领域事件 | 不直接操作 HTTP 请求/响应 |
| Manager | 单一业务领域的核心逻辑、设计模式应用、复杂业务规则 | 不跨领域编排（跨领域逻辑放 Service） |
| DAO | 数据库访问、SQL 执行 | 不包含业务逻辑 |

### 3.3 数据对象流转

```
HTTP Request → DTO（入参对象）
                    ↓
              Controller 接收 DTO，调用 Service
                    ↓
              Service 将 DTO 转换为 Entity
                    ↓
              Manager 操作 Entity
                    ↓
              DAO 持久化 Entity
                    ↓
              Service 将 Entity 转换为 VO
                    ↓
              Controller 返回 VO（包装在统一响应结构中）
                    ↓
              HTTP Response → VO（出参对象）
```

**三类数据对象严格分离：**

| 对象类型 | 包路径 | 用途 |
|---|---|---|
| DTO (Data Transfer Object) | `com.club.management.xxx.dto` | 接收前端请求参数 |
| VO (View Object) | `com.club.management.xxx.vo` | 返回给前端的展示数据 |
| Entity | `com.club.management.xxx.entity` | 对应数据库表结构 |

---

## 4. 业务模块划分

### 4.1 模块总览

| 模块 | 包名 | 职责 |
|---|---|---|
| 用户与权限 | `com.club.management.user` | 用户注册/登录、RBAC 权限、JWT 鉴权 |
| 社团管理 | `com.club.management.club` | 社团创建/审批、成员管理、信息维护 |
| 活动管理 | `com.club.management.activity` | 活动创建/审批、状态机、活动总结 |
| 报名与签到 | `com.club.management.registration` | 活动报名、签到、并发控制 |
| 资源管理 | `com.club.management.resource` | 场地管理、场地预约、设备借用 |
| 通知系统 | `com.club.management.notification` | 系统通知、社团公告、异步发送 |
| 数据统计 | `com.club.management.statistics` | 活动统计、社团考核评分 |

### 4.2 模块依赖关系

```
statistics ──→ activity ──→ club ──→ user
registration ──→ activity
notification ──→ activity, club
resource ──→ activity
```

- `user` 是基础模块，被所有模块依赖
- `club` 依赖 `user`
- `activity` 依赖 `club` 和 `user`
- `registration` 依赖 `activity`
- `notification` 依赖 `activity` 和 `club`
- `resource` 依赖 `activity`
- `statistics` 依赖 `activity`

---

## 5. 项目目录结构

### 5.1 后端目录结构

```
club-management/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/club/management/
│   │   │       ├── ClubManagementApplication.java          # 启动类
│   │   │       │
│   │   │       ├── common/                                  # 公共模块
│   │   │       │   ├── config/                              # 全局配置
│   │   │       │   │   ├── RedisConfig.java
│   │   │       │   │   ├── SecurityConfig.java
│   │   │       │   │   ├── MyBatisPlusConfig.java
│   │   │       │   │   └── WebMvcConfig.java
│   │   │       │   │
│   │   │       │   ├── exception/                           # 异常体系
│   │   │       │   │   ├── BusinessException.java           # 业务异常基类
│   │   │       │   │   ├── ErrorCode.java                   # 错误码枚举
│   │   │       │   │   ├── AuthException.java               # 认证异常
│   │   │       │   │   ├── ForbiddenException.java          # 权限异常
│   │   │       │   │   ├── NotFoundException.java           # 资源不存在
│   │   │       │   │   └── GlobalExceptionHandler.java      # 全局异常处理器
│   │   │       │   │
│   │   │       │   ├── response/                            # 统一响应
│   │   │       │   │   ├── R.java                           # 统一响应类
│   │   │       │   │   └── PageResult.java                  # 分页响应类
│   │   │       │   │
│   │   │       │   ├── annotation/                          # 自定义注解
│   │   │       │   │   ├── OperationLog.java                # 操作日志注解
│   │   │       │   │   ├── Idempotent.java                  # 幂等性注解
│   │   │       │   │   └── RequirePermission.java           # 权限注解
│   │   │       │   │
│   │   │       │   ├── aspect/                              # AOP 切面
│   │   │       │   │   ├── OperationLogAspect.java          # 操作日志切面
│   │   │       │   │   └── IdempotentAspect.java            # 幂等性切面
│   │   │       │   │
│   │   │       │   ├── event/                               # 领域事件
│   │   │       │   │   ├── ActivityApprovedEvent.java
│   │   │       │   │   ├── ActivityCompletedEvent.java
│   │   │       │   │   └── MemberJoinedEvent.java
│   │   │       │   │
│   │   │       │   ├── enums/                               # 公共枚举
│   │   │       │   │   ├── ActivityStatus.java              # 活动状态枚举
│   │   │       │   │   ├── ApprovalStatus.java              # 审批状态枚举
│   │   │       │   │   └── RoleType.java                    # 角色类型枚举
│   │   │       │   │
│   │   │       │   ├── util/                                # 工具类
│   │   │       │   │   ├── JwtUtil.java
│   │   │       │   │   └── SecurityUtil.java                # 获取当前登录用户
│   │   │       │   │
│   │   │       │   └── interceptor/                         # 拦截器
│   │   │       │       └── TenantInterceptor.java           # 多租户数据隔离
│   │   │       │
│   │   │       ├── user/                                    # 用户模块
│   │   │       │   ├── controller/
│   │   │       │   │   └── UserController.java
│   │   │       │   ├── service/
│   │   │       │   │   ├── UserService.java                 # 接口
│   │   │       │   │   └── impl/
│   │   │       │   │       └── UserServiceImpl.java         # 实现
│   │   │       │   ├── manager/
│   │   │       │   │   └── AuthManager.java
│   │   │       │   ├── mapper/
│   │   │       │   │   ├── UserMapper.java
│   │   │       │   │   └── RoleMapper.java
│   │   │       │   ├── entity/
│   │   │       │   │   ├── User.java
│   │   │       │   │   ├── Role.java
│   │   │       │   │   └── UserRole.java
│   │   │       │   ├── dto/
│   │   │       │   │   ├── LoginDTO.java
│   │   │       │   │   ├── RegisterDTO.java
│   │   │       │   │   └── UserUpdateDTO.java
│   │   │       │   └── vo/
│   │   │       │       ├── UserVO.java
│   │   │       │       └── LoginVO.java
│   │   │       │
│   │   │       ├── club/                                    # 社团模块（结构同 user）
│   │   │       │   ├── controller/
│   │   │       │   ├── service/
│   │   │       │   ├── manager/
│   │   │       │   ├── mapper/
│   │   │       │   ├── entity/
│   │   │       │   ├── dto/
│   │   │       │   └── vo/
│   │   │       │
│   │   │       ├── activity/                                # 活动模块
│   │   │       │   ├── controller/
│   │   │       │   ├── service/
│   │   │       │   ├── manager/
│   │   │       │   │   ├── ActivityStateManager.java        # 状态机管理
│   │   │       │   │   └── ApprovalManager.java             # 审批逻辑
│   │   │       │   ├── mapper/
│   │   │       │   ├── entity/
│   │   │       │   ├── dto/
│   │   │       │   ├── vo/
│   │   │       │   └── statemachine/                        # 状态机定义
│   │   │       │       └── ActivityStateMachine.java
│   │   │       │
│   │   │       ├── registration/                            # 报名签到模块
│   │   │       │   ├── controller/
│   │   │       │   ├── service/
│   │   │       │   ├── manager/
│   │   │       │   ├── mapper/
│   │   │       │   ├── entity/
│   │   │       │   ├── dto/
│   │   │       │   └── vo/
│   │   │       │
│   │   │       ├── resource/                                # 资源模块
│   │   │       │   ├── controller/
│   │   │       │   ├── service/
│   │   │       │   ├── manager/
│   │   │       │   ├── mapper/
│   │   │       │   ├── entity/
│   │   │       │   ├── dto/
│   │   │       │   └── vo/
│   │   │       │
│   │   │       ├── notification/                            # 通知模块
│   │   │       │   ├── controller/
│   │   │       │   ├── service/
│   │   │       │   ├── manager/
│   │   │       │   ├── mapper/
│   │   │       │   ├── entity/
│   │   │       │   ├── dto/
│   │   │       │   ├── vo/
│   │   │       │   └── listener/                            # 事件监听器
│   │   │       │       └── ActivityEventListener.java
│   │   │       │
│   │   │       └── statistics/                              # 统计模块
│   │   │           ├── controller/
│   │   │           ├── service/
│   │   │           ├── manager/
│   │   │           ├── mapper/
│   │   │           └── vo/
│   │   │
│   │   └── resources/
│   │       ├── application.yml                              # 主配置
│   │       ├── application-dev.yml                          # 开发环境配置
│   │       ├── application-prod.yml                         # 生产环境配置
│   │       └── mapper/                                      # MyBatis XML（复杂 SQL）
│   │           └── *.xml
│   │
│   └── test/
│       └── java/
│           └── com/club/management/
│               └── ...                                      # 测试类
│
├── sql/                                                      # 数据库脚本
│   ├── V1__init_schema.sql                                  # 初始化表结构
│   └── V2__init_data.sql                                    # 初始化数据
│
└── docker-compose.yml                                        # 容器编排（后续）
```

### 5.2 前端目录结构

```
club-management-web/
├── package.json
├── vite.config.js
├── index.html
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/
│   │   └── index.js                                          # 路由配置
│   ├── store/
│   │   └── index.js                                          # Pinia store
│   ├── api/                                                  # 接口请求
│   │   ├── request.js                                        # Axios 封装
│   │   ├── user.js
│   │   ├── club.js
│   │   ├── activity.js
│   │   ├── registration.js
│   │   ├── resource.js
│   │   ├── notification.js
│   │   └── statistics.js
│   ├── views/                                                # 页面
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── club/
│   │   ├── activity/
│   │   ├── registration/
│   │   ├── resource/
│   │   ├── notification/
│   │   └── statistics/
│   ├── components/                                           # 公共组件
│   ├── utils/                                                # 工具函数
│   └── styles/                                               # 全局样式
└── public/
```

---

## 6. 设计模式应用规划

| 模式 | 应用位置 | 解决的问题 |
|---|---|---|
| 状态模式 | `activity/statemachine/ActivityStateMachine.java` | 活动生命周期状态流转，非法转换拦截 |
| 策略模式 | `activity/manager/ApprovalManager.java` | 不同活动类型（校内/校外/大型）使用不同审批策略 |
| 观察者模式 | `notification/listener/ActivityEventListener.java` | 事件驱动解耦，审批/完成事件触发通知和统计 |
| 责任链模式 | `activity/manager/ApprovalManager.java` | 多级审批链（社长 → 指导老师 → 团委） |
| 模板方法 | `notification/manager/NotificationManager.java` | 通知发送流程固定（构建内容 → 选择渠道 → 发送） |
| 工厂模式 | `user/manager/RoleFactory.java` | 根据角色类型创建对应的权限策略 |
| 建造者模式 | `activity/dto/ActivityQueryDTO.java` | 多条件动态组合查询 |

---

## 7. 事件驱动设计

### 7.1 领域事件定义

| 事件类 | 触发时机 | 携带数据 |
|---|---|---|
| `ActivityApprovedEvent` | 活动审批通过 | activityId, clubId, approverId |
| `ActivityRejectedEvent` | 活动审批驳回 | activityId, clubId, reason |
| `ActivityCompletedEvent` | 活动结束 | activityId, clubId, participantCount |
| `MemberJoinedEvent` | 新成员加入社团 | userId, clubId |
| `MemberLeftEvent` | 成员退出社团 | userId, clubId |
| `RegistrationFullEvent` | 活动报名人数达上限 | activityId, maxCount |

### 7.2 事件流转

```
ActivityApprovedEvent
    ├── NotificationListener → 通知社团负责人活动已通过审批
    ├── StatisticsListener → 更新活动统计数据
    └── ResourceListener → 确认场地预约生效

ActivityCompletedEvent
    ├── NotificationListener → 通知参与者填写反馈
    └── StatisticsListener → 更新参与率统计

RegistrationFullEvent
    └── NotificationListener → 通知社团负责人报名已满
```

---

## 8. 并发与幂等设计

### 8.1 并发控制场景

| 场景 | 方案 | 实现方式 |
|---|---|---|
| 活动报名抢名额 | 乐观锁 | Entity 中增加 version 字段，MyBatis-Plus @Version |
| 场地预约冲突 | 数据库唯一约束 | 同一场地同一时段只允许一条预约记录 |
| 活动信息并发修改 | 乐观锁 | version 字段校验 |
| 签到防重复 | Redis SET | 签到码作为 key，用户 ID 作为 set member |

### 8.2 幂等性设计

| 接口 | 幂等方案 |
|---|---|
| 活动报名 | 数据库唯一约束（user_id + activity_id） |
| 签到 | Redis SET 去重 |
| 审批操作 | 状态机校验（已审批的活动不能重复审批） |
| 社团加入 | 数据库唯一约束（user_id + club_id） |

---

## 9. 异步处理设计

| 异步任务 | 触发场景 | 实现方式 |
|---|---|---|
| 通知发送 | 审批结果、活动提醒、报名成功 | `@Async` + 线程池 |
| 统计更新 | 活动状态变更、报名完成 | Spring Event 异步监听 |
| 批量操作 | 批量导入成员 | 异步执行 + 进度查询接口 |

线程池配置（`application.yml`）：
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
      thread-name-prefix: async-executor-
```

---

## 10. 限流设计

**应用位置：** 签到接口（活动开始时集中访问）

**实现方案：** Redis + Lua 脚本，滑动窗口限流

**配置：**
- 签到接口：每用户每秒最多 1 次请求
- 超出限制返回 HTTP 429，响应体包含 `code: 42900, message: "请求过于频繁，请稍后重试"`

---

## 11. 数据审计设计

**所有业务表包含审计字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `created_by` | BIGINT | 创建人 ID |
| `created_at` | DATETIME | 创建时间 |
| `updated_by` | BIGINT | 最后修改人 ID |
| `updated_at` | DATETIME | 最后修改时间 |
| `deleted` | TINYINT | 逻辑删除标记（0=未删除, 1=已删除） |

**实现方式：** MyBatis-Plus MetaObjectHandler 自动填充

**变更日志表 `operation_log`：** 记录关键操作的字段级变更

---

## 12. 多租户数据隔离

**隔离维度：** 社团（club_id）

**实现方式：** MyBatis-Plus TenantLineInnerInterceptor

- 所有业务表包含 `club_id` 字段
- 拦截器自动在 SQL 中追加 `WHERE club_id = ?`
- 系统管理员角色绕过租户过滤
- 通过 `ThreadLocal` 传递当前用户的 club_id

---

## 13. 后续 AI 层预留

**预留接口规范：**

- Java 后端为所有业务操作提供 REST API
- AI 层（Python FastAPI）通过 HTTP 调用 Java 后端 API
- 预留 AI 服务调用接口：`/api/v1/ai/` 前缀
- 预留 Webhook 回调机制：Agent 执行结果可回调通知 Java 后端

**预留的 AI 接口路径（暂不实现）：**
```
POST /api/v1/ai/approval/evaluate      # 智能审批评估
POST /api/v1/ai/activity/plan          # 活动规划
GET  /api/v1/ai/knowledge/query        # 制度问答
POST /api/v1/ai/resource/schedule      # 资源调度
```
