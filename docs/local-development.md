# 本地前后端分离运行

前端和后端是两个独立进程。前端位于 `club-management-web/`，使用 Vue 3 / Vite；后端位于 `src/`，使用 Spring Boot 3 / Java 17，通过 `/api/v1` 提供 JSON 接口和 JWT 认证。数据库与 Redis 不对浏览器开放。

```text
浏览器 → Vue (5173) → Java REST API (8081) → MySQL (3307) / Redis (6379)
```

配置根目录 `.env` 中的 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 和 `AI_INTERNAL_API_KEY` 后，启动脚本会同时启动 Python AI 服务。未配置密钥时仍可独立运行社团管理功能。

## 本机启动

### Windows / Mac 双击入口

| 操作 | Windows | macOS | 终端 |
| --- | --- | --- | --- |
| 启动整套服务 | `start.cmd` | `start.command` | `./start.sh` |
| 停止并保留数据 | `stop.cmd` | `stop.command` | `./stop.sh` |

这些文件都调用 `scripts/dev.py`，依次启动项目专用 MySQL、Redis、Java 后端和 Vue 前端。已有服务由进程记录识别，不重复启动；外部进程占用端口时会报错，不会自动结束其他程序。关闭启动窗口不会停止后台服务，需要停止时使用停止入口。

首次使用需安装 Python 3.10+、JDK 17+、Node.js 18+（含 npm）、Maven 3.8+、MySQL 服务端及客户端、Redis 兼容服务端及客户端，并将工具加入 PATH。Windows 不需要注册项目数据库为系统服务，也不需要管理员权限。Redis 可使用已安装的 `redis-server.exe` / `redis-cli.exe`，或 [Redis 官方 Windows 说明](https://redis.io/docs/latest/operate/oss_and_stack/install/archive/install-redis/install-redis-on-windows/)中的 Memurai，其 `memurai.exe` / `memurai-cli.exe` 会自动识别。仅安装 WSL 中的 Redis 不会提供本脚本所需的 Windows 原生可执行文件。

如果工具没有加入 PATH，在根目录 `.env` 中追加实际安装目录，例如：

```dotenv
MYSQL_BIN=C:/Program Files/MySQL/MySQL Server 8.0/bin
REDIS_BIN=C:/Program Files/Memurai
JAVA_HOME=C:/Program Files/Java/jdk-17
MAVEN_HOME=C:/Tools/apache-maven-3.9.9
```

前两个变量指向包含服务端和客户端的目录，后两个变量指向软件根目录。填写自己电脑的路径；中文和空格无需额外加引号。默认查找 PATH、Windows 常见 MySQL / Memurai / IDEA 安装目录，以及当前 Mac 的 MySQL、Homebrew 和 IDEA Maven 目录。

Mac 的 `.command` 已设置执行权限。压缩软件若丢失权限，在项目根目录执行 `chmod +x start.command stop.command start.sh stop.sh scripts/run.sh` 后再双击。脚本会自动切换到项目目录，因此可以从其他目录运行；终端脚本也可用于排查双击窗口中的错误。

首次启动会联网下载 Maven/npm 依赖，耗时取决于网络。无需手动复制 `.env.example`：缺少 `.env` 时脚本生成随机数据库密码和 JWT 密钥；存在 `.env` 时保留原配置。数据保存在 `.runtime/mysql/` 与 `.runtime/redis/`，上传文件仍在原目录；停止和重启不会清库或重复导入已执行的 SQL。已有数据库密码不匹配时会中止，不会自动重置密码。

默认 MySQL 端口为 3307，Redis 为 6379。如果安装软件已自动运行系统数据库服务并占用了项目端口，可在 `.env` 修改 `MYSQL_PORT` / `REDIS_PORT`；AI 用户还需同步 `REDIS_URL`。项目脚本只管理自己记录的进程，不接管系统服务。不要把本机 `.env`、`.runtime/`、`node_modules/`、`target/` 或 Python `.venv/` 直接复制给其他平台；分发源码后在目标电脑首次启动生成本地环境。

Windows 相关的工具发现、参数和路径处理有脚本级测试；当前环境为 macOS，没有 Windows 实机启动、数据库连接和构建验证，交付 Windows 用户前需在目标电脑运行一次完整流程。

### 直接使用 Python

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

启动时会比较 `pom.xml`、Java 源码和后端资源与 JAR 的修改时间，必要时重新测试打包；正在运行旧后端时只停止本项目后端并重建。需要强制构建时仍可执行 `setup`。修改 Vue 页面时由 Vite 自动更新。

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
