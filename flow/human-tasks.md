# 人工处理任务清单

> 以下任务需要你在本地环境中手动完成，涉及环境搭建、外部服务配置、运行验证等。  
> 编码 Agent 无法替代这些操作。

---

## 1. 本地环境确认与启动

- [ ] 确认 MySQL 8.0 已启动，数据库 `club_management` 已创建
- [ ] 执行 `sql/V1~V4` 初始化表结构和数据（若未执行）
- [ ] 确认 Redis 已启动（`localhost:6379`）
- [ ] 确认 Java 17 已安装（`java -version`）
- [ ] 确认 Maven 已安装（`mvn -version`）
- [ ] 确认 Node.js 18+ 已安装（`node -version`）
- [ ] 确认 Python 3.10+ 已安装（`python --version`）

---

## 2. Docker 环境搭建

- [ ] 安装 Docker Desktop（https://www.docker.com/products/docker-desktop/）
- [ ] 启动 Docker Desktop，确认 `docker --version` 正常
- [ ] 执行 `docker-compose build` 构建三个镜像
- [ ] 执行 `docker-compose up` 启动所有服务
- [ ] 验证：浏览器访问 `http://localhost` 能看到前端页面
- [ ] 验证：API 请求正常（登录、查询等）
- [ ] 验证：`docker-compose down` 能正常停止

---

## 3. 数据库索引执行

- [ ] 在 MySQL 中执行 `sql/V5__add_indexes.sql`（Agent 生成后）
- [ ] 用 `SHOW INDEX FROM 表名` 确认索引已创建
- [ ] 用 `EXPLAIN SELECT ...` 对几条核心查询验证索引命中

---

## 4. 三端联调验证

### 4.1 后端启动
- [ ] `mvn spring-boot:run` 启动 Java 后端（端口 8081）
- [ ] 访问 `http://localhost:8081/swagger-ui.html` 确认 Swagger 文档正常

### 4.2 AI 服务启动
- [ ] `cd ai-service && pip install -r requirements.txt`
- [ ] `uvicorn main:app --port 8000` 启动 Python 服务
- [ ] 访问 `http://localhost:8000/docs` 确认 FastAPI 文档正常

### 4.3 前端启动
- [ ] `cd club-management-web && npm install`
- [ ] `npm run dev` 启动前端（端口 5173）
- [ ] 访问 `http://localhost:5173` 确认页面正常

### 4.4 联调验证
- [ ] 登录 → 获取 Token → 进入 Dashboard
- [ ] AI 对话功能正常（发送消息 → 收到回答）
- [ ] SSE 流式输出效果正常（逐字显示）
- [ ] 活动创建 → 审批 → 报名 → 签到 完整流程跑通

---

## 5. GitHub 仓库与 CI 配置

- [ ] 创建 GitHub 仓库（若尚未创建）
- [ ] `git init && git add . && git commit -m "init"`
- [ ] `git remote add origin <仓库地址> && git push`
- [ ] 在 `.github/workflows/` 下添加 CI 配置（Agent 可写文件，但推送和触发需人工）
- [ ] 确认 CI 流水线触发并执行成功

---

## 6. 简历文案撰写

- [ ] 根据项目整理文档，提炼核心亮点
- [ ] 撰写简历上的项目描述（参考之前给出的模板）
- [ ] 准备面试高频问题的回答（见项目整理文档末尾）
- [ ] 量化指标：表数量、权限码数量、RAG 管道节点数等

---

## 7. 测试运行与验证

- [ ] Agent 写完单元测试后，执行 `mvn test` 确认全部通过
- [ ] 若有失败用例，反馈给 Agent 修改
- [ ] 确认无测试被 `@Disabled` 跳过

---

## 8. 最终检查清单

- [ ] 项目能 `docker-compose up` 一键启动
- [ ] 所有功能流程正常
- [ ] 单元测试全部通过
- [ ] Swagger 文档可访问
- [ ] GitHub 仓库已推送
- [ ] CI 流水线绿色通过
- [ ] 简历文案已定稿
