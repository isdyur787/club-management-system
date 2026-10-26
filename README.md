# 社团管理系统（Club Management System）

面向高校社团管理的智能平台，涵盖社团管理、活动审批、报名签到、资源借用、通知推送、积分打卡等完整业务场景，并深度集成了基于 RAG（检索增强生成）的 AI 智能助手。

## 系统架构

```
┌──────────────────┐     REST      ┌──────────────────┐     REST      ┌──────────────────┐
│   Vue 3 前端      │ ──────────── │  Java 后端        │ ──────────── │  Python AI 服务   │
│  Vite + Element   │   :5173      │  Spring Boot 3    │   :8081      │  FastAPI + RAG    │
└──────────────────┘              └────────┬─────────┘              └────────┬─────────┘
                                           │                                 │
                              MySQL 8.0 + Redis 6.0              ChromaDB + BGE 模型
```

| 子系统 | 技术栈 | 端口 |
|--------|--------|------|
| **前端** | Vue 3 + Vite 5 + Element Plus + Pinia | 5173 (dev) |
| **后端** | Spring Boot 3.2.5 + Java 17 + MyBatis-Plus + Spring Security + JWT | 8081 |
| **AI 服务** | FastAPI + LangGraph + ChromaDB + Sentence-Transformers | 8000 |

## 功能模块

### 核心业务
- **用户认证**：JWT 无状态认证、RBAC 权限（4 角色 24+ 权限码）、登录失败锁定
- **社团管理**：社团 CRUD、成员加入/退出、社团审批
- **活动管理**：活动全生命周期（状态机：草稿 → 待审批 → 已审批 → 进行中 → 已完成）、策略模式审批
- **报名签到**：活动报名、签到码签到、签退
- **资源管理**：场地预约、设备借用/归还
- **通知推送**：Spring Event 事件驱动自动通知
- **积分系统**：签到积分、打卡积分、建议采纳积分
- **打卡管理**：打卡任务创建、打卡记录、打卡汇编

### AI 智能助手
- **RAG 知识问答**：混合检索（向量 + BM25 + RRF 融合）→ Reranker 精排 → LLM 生成 → 忠实度自检
- **成果报告生成**：基于打卡汇编数据自动生成活动成果报告
- **经验推荐**：检索历史活动报告为新活动提供经验建议
- **Function Calling**：Agent 自动调用数据库查询（活动搜索、成员查询）
- **知识库管理**：文档上传入库、报告确认入库，支持 RAG 检索
- **多模型降级**：DeepSeek V4-Flash（主）→ MiMo v2.5（备），自动故障转移

## 快速开始

### 本机一键运行（前后端分离）

```bash
python3 scripts/dev.py start
```

访问 `http://localhost:5173`，管理员账号为 `admin / 123456`。该命令独立启动 Vue 前端、Java 后端及项目专用 MySQL / Redis，不启动 Python AI 服务。首次运行自动配置 `.env` 并初始化数据库；停止使用 `python3 scripts/dev.py stop`，数据保留。完整说明见 [本地运行与部署配置](docs/local-development.md)。

下面是使用已有数据库、手动配置所有服务的步骤：

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Python | 3.10+ |
| Node.js | 18+ |
| MySQL | 8.0 |
| Redis | 6.0+ |

### 1. 克隆项目

```bash
git clone https://gitee.com/rirusoon/club-management-system.git
cd club-management-system
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入实际的 API Key、数据库密码等
```

### 3. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS club_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p < sql/V1__init_schema.sql
mysql -u root -p club_management < sql/V2__init_data.sql
mysql -u root -p club_management < sql/V3__ai_tables.sql
mysql -u root -p club_management < sql/V4__ai_permissions.sql
mysql -u root -p club_management < sql/V5__add_indexes.sql
mysql -u root -p club_management < sql/V6__test_data.sql
mysql -u root -p club_management < sql/V7__fix_issues.sql
mysql -u root -p club_management < sql/V8__bugfix.sql
mysql -u root -p club_management < sql/V9__add_compound_indexes.sql
mysql -u root -p club_management < sql/V10__more_test_data.sql
mysql -u root -p club_management < sql/V11__bulk_test_data.sql
mysql -u root -p club_management < sql/V12__sync_president_roles.sql
mysql -u root -p club_management < sql/V13__complete_demo_scenarios.sql
```

### 4. 启动 Java 后端

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger 文档：http://localhost:8081/swagger-ui/index.html

### 5. 启动 AI 服务

```bash
cd ai-service
pip install -r requirements.txt
python main.py
```

健康检查：http://localhost:8000/health

### 6. 启动前端

```bash
cd club-management-web
npm install
npm run dev
```

访问：http://localhost:5173

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 系统管理员 |
| teacher_li | 123456 | 指导老师 |
| zhangsan | 123456 | 学生 / 社团负责人 |
| lisi | 123456 | 学生 |

## 项目结构

```
club-management-system/
├── src/main/java/com/club/management/   # Java 后端
│   ├── common/                           # 公共模块（安全/注解/异常/工具）
│   ├── user/                             # 用户认证
│   ├── club/                             # 社团管理
│   ├── activity/                         # 活动管理（状态机 + 审批策略）
│   ├── registration/                     # 报名管理
│   ├── checkin/                          # 打卡管理
│   ├── signout/                          # 签退管理
│   ├── points/                           # 积分管理
│   ├── resource/                         # 资源管理
│   ├── notification/                     # 通知管理（事件驱动）
│   ├── statistics/                       # 数据统计
│   ├── file/                             # 文件管理
│   └── ai/                               # AI 模块（Java 端）
├── src/test/java/                        # 单元测试
├── ai-service/                           # Python AI 微服务
│   ├── routes/                           # 路由（chat/report/experience/knowledge）
│   ├── rag/                              # RAG 核心（检索/精排/生成/缓存/护栏）
│   ├── agents/                           # Agent 系统（模型路由/工具执行）
│   ├── tools/                            # 工具（DB 读取/文档解析）
│   └── tests/                            # 测试
├── club-management-web/                  # Vue 3 前端
│   └── src/
│       ├── api/                          # API 请求层
│       ├── components/                   # 可复用组件
│       ├── composables/                  # 组合式函数
│       ├── constants/                    # 常量枚举
│       ├── router/                       # 路由配置（角色守卫）
│       ├── store/                        # Pinia 状态管理
│       ├── utils/                        # 工具函数
│       └── views/                        # 页面组件
├── sql/                                  # 数据库脚本（V1 ~ V11）
├── docs/                                 # 设计文档
├── pom.xml                               # Maven 配置
└── .env.example                          # 环境变量模板
```

## 技术亮点

| 维度 | 实现 |
|------|------|
| **权限安全** | RBAC 4 角色 + 24 权限码、JWT + Redis 黑名单、登录失败锁定、XSS 白名单过滤 |
| **活动状态机** | 策略模式审批（校内/大型/校外三种策略）、乐观锁防并发 |
| **事件驱动** | Spring Event 解耦通知推送（审批通过/活动完成/成员加入） |
| **AOP 横切面** | 幂等控制、接口限流、操作日志、权限校验四大切面 |
| **RAG 管道** | LangGraph 6 节点状态图、向量+BM25 混合检索、RRF 融合、Reranker 精排、忠实度自检 |
| **Agent 系统** | 多模型路由降级、Function Calling 工具注册、参数白名单校验 |
| **延迟优化** | 语义缓存（LRU）、Embedding 缓存、检索缓存、LLM 并发控制 |
| **三端分离** | Java 业务 + Python AI + Vue 前端，职责清晰，独立部署 |

## 设计文档

| 文档 | 内容 |
|------|------|
| [架构设计](docs/01-architecture.md) | 系统架构、技术选型、部署架构 |
| [开发规范](docs/02-development-standards.md) | 编码规范、分层规范、提交规范 |
| [数据库设计](docs/03-database-design.md) | ER 图、表结构、索引策略 |
| [API 规范](docs/04-api-specification.md) | RESTful 接口规范、统一响应格式 |
| [模块设计](docs/05-module-design.md) | 各模块详细设计 |
| [安全设计](docs/06-security-design.md) | 认证鉴权、RBAC、数据安全 |

## 许可证

本项目仅供学习交流使用。
