作者：大连工业大学计算机互认 231 第一组（邵昱铭、黄宸歆、钱程、吕金凌、李惠恩）

# 社团管理系统

本项目用于小组课程实训与项目答辩。组员按功能模块分工，使用 Git 管理代码版本、同步开发进度和合并各自完成的功能。

系统围绕高校社团的日常管理，提供社团与成员管理、活动审批、报名签到、资源借用、通知、积分打卡等功能，并结合社团知识资料提供 AI 问答与报告辅助生成。

## 主要功能

- 用户与权限：登录认证、角色权限控制、个人信息管理。
- 社团与活动：社团申请、成员管理、活动发布与审批、报名、签到和签退。
- 日常管理：场地预约、设备借用、消息通知、打卡记录和积分统计。
- AI 助手：社团知识问答、活动信息查询、经验推荐和成果报告辅助生成。
- 知识库：上传资料、管理文档，通过混合检索和重排序为 AI 回答提供参考内容。

## 技术结构

| 部分           | 技术与用途                                        |
| -------------- | ------------------------------------------------- |
| 前端           | Vue 3、Vite、Element Plus、Pinia                  |
| Java 后端      | Spring Boot 3、MyBatis-Plus、Spring Security、JWT |
| Python AI 服务 | FastAPI、LangGraph、BGE 向量与重排序模型          |
| MySQL          | 保存用户、社团、活动、报名等业务数据              |
| Redis          | 保存缓存、会话上下文等数据                        |
| ChromaDB       | 保存知识片段及其向量，支持语义检索                |

请求流程：浏览器 → Vue 前端 → Java 业务后端 → MySQL / Redis；AI 相关请求由 Java 后端转发至 Python AI 服务，结合知识检索与云端模型接口生成结果。

## 本地运行

基础环境需要 Python 3.10+、JDK 17+、Node.js 18+、Maven、MySQL 8 和 Redis 兼容服务，相关命令需加入 PATH。AI 环境建议使用 Python 3.12。Windows 可使用启动脚本支持的 Memurai。

在项目根目录使用以下入口：

| 系统               | 启动                | 停止并保留数据     |
| ------------------ | ------------------- | ------------------ |
| Windows            | 双击`start.cmd`     | 双击`stop.cmd`     |
| macOS              | 双击`start.command` | 双击`stop.command` |
| macOS / Linux 终端 | `bash start.sh`     | `bash stop.sh`     |

也可以通过公共脚本启动、查看状态和停止服务：

```bash
python3 scripts/dev.py start
python3 scripts/dev.py status
python3 scripts/dev.py stop
```

首次运行且根目录没有 `.env` 时，脚本会生成本机配置、初始化项目专用数据库、安装前端依赖并构建后端。已有配置和数据会保留；系统环境变量优先于 `.env`。脚本会检查系统依赖，但不会自动安装 MySQL、Redis、Java 等系统软件。

默认访问地址：

- 前端：[http://localhost:5173](http://localhost:5173)
- 后端接口文档：[http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- AI 健康检查：[http://localhost:8000/health](http://localhost:8000/health)，需先启动 AI 服务。

### 启用 AI 服务

在根目录 `.env` 中配置模型 API 密钥 `LLM_API_KEY`、服务地址 `LLM_BASE_URL`、模型名称 `LLM_MODEL`，以及 Java 与 Python 之间的内部认证密钥 `AI_INTERNAL_API_KEY`。数据库配置需与本机项目数据库一致。

准备 AI 环境后启动服务：

```bash
python3 scripts/ai.py setup
python3 scripts/ai.py start
```

准备好 AI 环境和有效密钥后，整套项目的启动入口也会启动 AI 服务。向量模型与重排序模型分别通过 `EMBEDDING_MODEL`、`RERANKER_MODEL` 配置，在本地加载；回答生成模型通过 API 调用。

## 组员使用 Git 协作

小组使用共享 Git 仓库管理项目。每位组员负责自己的功能模块，在独立分支开发，将代码推送到共享仓库，经过组员检查后合并到 `main` 分支。

协作约定：

1. 开始开发前，先同步主分支，确认本地已有修改已妥善保存。
2. 按功能创建分支，例如 `feature/knowledge-upload`；修复问题可使用 `fix/` 前缀。
3. 提交前查看差异，只提交本次负责的修改，提交说明写清楚完成了什么。
4. 推送分支后，由相关组员检查接口、公共文件和运行情况，再合并到主分支。
5. 修改同一文件发生冲突时，相关组员共同确认保留的逻辑，解决后再提交。

以下以知识库上传页面的协作为例：

```bash
# 同步主分支并创建自己的功能分支
git switch main
git pull --ff-only origin main
git switch -c feature/knowledge-upload

# 完成开发后，查看并提交本次修改
git status
git diff
git add club-management-web/src/views/ai/KnowledgeView.vue
git commit -m "feat: 完善知识库上传功能"

# 推送到小组共享仓库，供组员检查与合并
git push -u origin feature/knowledge-upload
```

共享仓库提交源码、依赖清单、配置模板和 SQL。各组员的 `.env`、API 密钥、本地数据库、上传文件、模型权重和依赖安装目录保留在各自电脑，不作为协作代码提交。数据库结构调整使用新的 SQL 迁移文件，已执行过的历史迁移保持不变。

## 项目目录

```text
club-management-system/
├── src/main/                 # Java 业务后端与配置
├── club-management-web/      # Vue 前端
├── ai-service/               # Python AI 服务与知识检索
├── scripts/                  # 服务调度、环境准备和数据索引工具
├── sql/                      # 数据库初始化与版本迁移
├── docs/knowledge/           # SQL 引用的社团知识资料
├── deploy/                   # 部署配置示例
├── pom.xml                   # Java 依赖与构建配置
├── .env.example              # 环境配置参考
└── start.* / stop.*          # 各平台启动与停止入口
```

`.runtime/` 保存本机运行状态、数据库及相关运行文件；数据目录保存上传文件和 AI 服务数据，停止服务时保留这些内容。
