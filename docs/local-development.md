# 本地前后端分离运行

前端和后端是两个独立进程。前端位于 `club-management-web/`，使用 Vue 3 / Vite；后端位于 `src/`，使用 Spring Boot 3 / Java 17，通过 `/api/v1` 提供 JSON 接口和 JWT 认证。数据库与 Redis 不对浏览器开放。

```text
浏览器 → Vue (5173) → Java REST API (8081) → MySQL (3307) / Redis (6379)
```

配置根目录 `.env` 中的 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 和 `AI_INTERNAL_API_KEY` 后，启动脚本会同时启动 Python AI 服务。未配置密钥时仍可独立运行社团管理功能。

## 本机启动

在项目根目录执行：

```bash
python3 scripts/dev.py start
```

首次运行自动生成根目录 `.env`，随机生成项目数据库密码和 JWT 密钥，在 `.runtime/mysql/` 初始化独立 MySQL，按版本执行 SQL，并安装前端依赖、构建后端。已有 `.env` 不会被覆盖。脚本自动寻找 JDK 17 和 IDEA 内置 Maven，也支持 PATH 中的 Maven。

依赖：Python 3、Java 17、Node.js 18+、MySQL 服务端及客户端、Redis。此 Mac 使用已安装的 `/usr/local/mysql/bin/`。本机 Homebrew 不支持当前 macOS，Redis 已单独编译到 `.runtime/tools/`；需要重装时执行：

```bash
sh scripts/install-redis.sh
```

| 服务 | 本机地址 |
| --- | --- |
| 前端登录 | http://localhost:5173/login |
| 后端 API | http://localhost:8081/api/v1 |
| Swagger | http://localhost:8081/swagger-ui/index.html |
| MySQL | 127.0.0.1:3307 / club_management |
| Redis | 127.0.0.1:6379 / DB 0 |
| AI 健康检查 | http://127.0.0.1:8000/health |

测试账号：`admin / 123456`（管理员）、`teacher_li / 123456`（老师）、`zhangsan / 123456`（社团负责人）、`lisi / 123456`（学生）。数据库密码见根目录 `.env`，与网页登录密码不同。

```bash
python3 scripts/dev.py status  # 查看本项目五个服务
python3 scripts/dev.py stop    # 仅停止脚本记录的本项目进程，保留数据
python3 scripts/dev.py start   # 重新启动，不重复导入种子数据
python3 scripts/dev.py setup   # 修改 Java 后重新测试打包，同时构建前端
```

修改 Java 后：先 `stop`，再 `setup`，最后 `start`。修改 Vue 页面时由 Vite 自动更新。

SQL 版本记录存放在 `_local_schema_history`，已执行脚本会校验 SHA-256 并跳过。新增数据库变更请新增更高版本的 SQL，不要修改已执行脚本。本次修复了原始 V5 的重复索引，以及 V10 / V11 中缺少社团字段、重复签到和重复报名的初始化错误；V12 为已审批社团的负责人补齐原种子数据遗漏的 RBAC 角色。

日志：`.runtime/backend.log`、`.runtime/frontend.log`、`.runtime/mysql.log`、`.runtime/redis.log`、`.runtime/ai.log`。`.runtime/` 和 `.env` 不参与版本控制。系统原有 MySQL 实例和其他数据库不受本脚本影响。

## AI 服务

本机使用 `qwen3.8-flash`，通过百炼兼容接口调用；密钥仅保存在根目录 `.env`。中文向量及重排模型已下载到忽略的 `.runtime/models/`，Python 3.12 环境位于 `ai-service/.venv/`。

```bash
python3 scripts/ai.py start   # 单独启动 AI，不重启 IDEA 后端
python3 scripts/ai.py status  # 检查服务与模型配置，不显示密钥
python3 scripts/ai.py index   # 根据现有文档重建知识库索引
python3 scripts/ai.py stop    # 仅停止 AI
```

首次配置其他机器时，先填写 `.env.example` 所示模型配置，再使用 Python 3.12 执行 `python3 scripts/ai.py setup` 安装依赖并加载模型。数据库和 Redis 必须已启动。当前依赖版本已在本机 macOS arm64 验证；其他平台需另行验证。

登录后进入「AI 助手」提问；「知识库」可选择社团上传 txt/md/docx；活动详情中的「成果报告」可生成、打回重新生成、确认入库。演示活动「AI功能演示：编程交流与复盘」（ID 44）包含三条演示反馈和汇编，可查看完整报告。活动 ID 由当前本地数据库分配，其他机器请在活动列表中查找标题。

「AI 助手」按账号和社团保存本次登录的聊天记录，切换页面、切换社团后返回或刷新页面都可恢复；点击「退出登录」会清空本地记录，下次登录开启新会话。回复尚未完成时切换页面，结果仍会保留在原社团对话中。

`docs/knowledge/` 新增了街舞社、英语角、电竞社、书法协会、环保社的招新指南和 FAQ，以及四个申请阶段社团的筹备指引，共 14 份演示文档。V15 迁移登记仓库相对路径，执行迁移后运行 `python3 scripts/ai.py index` 建立索引。可在对应社团提问「街舞社第一次体验课要准备什么」「英语角口语活动前准备哪些内容」「电竞社有哪些非选手岗位」，查看回答中的参考来源。

## 积分中心

页面提供个人积分、个人排名、本月/累计社团积分、近7日变化、来源分布、个人/社团流水与排行榜。管理员和教师可切换全部已审批社团，普通成员可切换自己加入的已审批社团。管理员没有个人积分时显示「未上榜」，默认展示社团动态。

V15 为已有活跃成员补充 396 条演示积分流水，说明统一以「演示积分：」开头；不新增虚构成员，也不覆盖旧积分记录。重复执行不会重复加分。之后产生的真实积分仍按原有活动签到、打卡和建议采纳流程记录。

代码更新后重新构建后端再启动，避免仍运行旧 JAR。当前一键启动方式为：`python3 scripts/dev.py stop` → `python3 scripts/dev.py setup` → `python3 scripts/dev.py start`。

## 分别启动与配置

后端从工作目录读取根目录 `.env`，也支持系统环境变量。数据库参数为 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`，完整 JDBC 地址可用 `MYSQL_URL` 覆盖；端口为 `SERVER_PORT`。

在 IDEA 导入根目录 `pom.xml`，项目 SDK 选择 Java 17，工作目录设置为项目根目录，运行 `ClubManagementApplication`。需提前启动 MySQL 和 Redis；如果它们已经运行，可直接启动后端。

如果后端已由脚本占用 8081，先执行 `python3 scripts/dev.py stop-backend`，再点击 IDEA 的运行按钮；数据库、Redis、前端和 AI 会继续运行。

也可以在终端运行后端：

```bash
mvn spring-boot:run
```

前端：

```bash
cd club-management-web
npm ci
npm run dev
```

`club-management-web/.env.development` 默认直接请求 `http://localhost:8081/api/v1`，跨域白名单通过后端 `CORS_ORIGINS` 配置。Axios 和 SSE 共用 `src/api/config.js` 的地址。

前端开发环境覆盖写入 `club-management-web/.env.development.local`，修改后重启 Vite；生产环境覆盖使用 `.env.production.local`：

```dotenv
VITE_API_BASE_URL=http://localhost:8081/api/v1
VITE_API_TARGET=http://127.0.0.1:8081
VITE_PORT=5173
```

如果更改端口，两端配置要保持一致：`VITE_API_BASE_URL` / `VITE_API_TARGET` 对应后端 `SERVER_PORT`；后端 `CORS_ORIGINS` 对应浏览器实际前端来源。`VITE_` 变量会进入浏览器，禁止在其中放数据库密码或密钥。

## 构建后独立部署

```bash
cd club-management-web
npm run build
npm run preview
```

预览地址为 `http://127.0.0.1:4173`，通过 Vite 的 `/api` 代理连接后端。

生产环境将 `dist/` 交给 Nginx 等静态服务器托管，Java JAR 单独运行。默认构建请求同源 `/api/v1`，反向代理示例见 `deploy/nginx.conf.example`；该示例未修改或启动本机现有 Nginx。若前端和后端使用不同域名，构建前设置 `VITE_API_BASE_URL` 为后端完整地址，并将前端域名加入后端白名单。

后端生产环境设置 `SPRING_PROFILES_ACTIVE=prod`，配置 `.env.example` 中的 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`、Redis、`JWT_SECRET` 等项目。当前生成的 `.env` 是本地开发配置，不用于公网部署。

配置依据：[Spring Security CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)、[Vite 环境配置](https://vite.dev/config/)。
