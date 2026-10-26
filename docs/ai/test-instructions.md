# 测试数据生成与集成验证指令

> 面向编码 LLM 的测试阶段指令。本文档包含：测试数据生成、Python 服务启动、全链路验证。

## 身份定义

你是全栈工程师（Java Spring Boot + Python FastAPI + Vue 3），负责执行测试数据生成和服务验证。

## 核心规则

1. **按顺序执行**：先生成测试数据，再启动 Python 服务，最后验证全链路
2. **数据库操作谨慎**：执行 SQL 前确认表存在
3. **日志观察**：每步操作后观察后端/Python 日志，确认无异常

---

## 第一部分：测试数据生成

### 1.1 确认 AI 表已创建

首先确认 8 张 AI 新表存在。连接 MySQL 执行：

```sql
SHOW TABLES LIKE 'act_checkin%';
SHOW TABLES LIKE 'act_sign%';
SHOW TABLES LIKE 'ai_%';
SHOW TABLES LIKE 'member_points%';
```

如果表不存在，执行 `docs/ai/02-ai-database.md` 中的 `V3__ai_tables.sql` 和 `V4__ai_permissions.sql`。

### 1.2 查看现有数据状态

```sql
-- 查看社团
SELECT id, club_name FROM club_info WHERE deleted = 0;

-- 查看用户
SELECT id, username, real_name FROM sys_user WHERE deleted = 0;

-- 查看活动（重点关注状态为 COMPLETED 的活动）
SELECT id, club_id, title, status, start_time, end_time 
FROM act_activity WHERE deleted = 0;

-- 查看已有打卡任务
SELECT id, activity_id, deadline, status FROM act_checkin_task WHERE deleted = 0;

-- 查看已有打卡记录
SELECT id, task_id, activity_id, user_id, rating FROM act_checkin_record WHERE deleted = 0;

-- 查看已有汇编
SELECT id, activity_id, member_count FROM act_checkin_compilation WHERE deleted = 0;
```

### 1.3 创建测试用打卡数据

目标：为活动 ID=4（Web Workshop，状态 COMPLETED）创建完整的测试数据链。

**步骤 A：调整打卡截止时间为过去（使汇编条件满足）**

```sql
-- 将打卡任务截止时间改为昨天，使系统认为已截止
UPDATE act_checkin_task 
SET deadline = DATE_SUB(NOW(), INTERVAL 1 HOUR),
    status = 2  -- 已截止
WHERE activity_id = 4 AND deleted = 0;
```

**步骤 B：插入多条打卡记录（模拟多个成员提交）**

```sql
-- 确认已有 user_id=1 的记录，补充 user_id=2 的记录
-- 先检查是否已有 user_id=2 的记录
SELECT * FROM act_checkin_record WHERE activity_id = 4 AND user_id = 2 AND deleted = 0;

-- 如果没有，插入第二条打卡记录
INSERT INTO act_checkin_record 
(task_id, activity_id, club_id, user_id, rating, reflection, problem, suggestion, photo_urls, image_descriptions, file_urls, is_adopted, status, created_at, updated_at)
SELECT 
    id, 4, 1, 2, 4,
    '这次 Workshop 的实操环节让我收获很大，终于理解了 Vue3 的 Composition API 和 Options API 的区别。',
    '实操环节时间有点紧，很多代码还没来得及调试就结束了。',
    '建议下次把实操时间延长一些，最好能安排 2 小时以上的动手环节。',
    '[]', '[]', '[]', 0, 1, NOW(), NOW()
FROM act_checkin_task 
WHERE activity_id = 4 AND deleted = 0
AND NOT EXISTS (
    SELECT 1 FROM act_checkin_record WHERE activity_id = 4 AND user_id = 2 AND deleted = 0
);

-- 再插入第三条记录（user_id=1 的第二条，如果允许的话；或者更新已有记录）
-- 更新 user_id=1 的记录，补充更丰富的内容
UPDATE act_checkin_record 
SET reflection = '这次分享会对 Vue3 的讲解很清晰，特别是响应式原理和 Proxy 的部分，让我对前端框架的底层实现有了更深入的理解。',
    problem = '理论讲解部分有些快，Proxy 的递归代理那块没太听明白。',
    suggestion = '建议在讲解复杂概念时增加一些图示或动画辅助，帮助理解。'
WHERE activity_id = 4 AND user_id = 1 AND deleted = 0;
```

**步骤 C：验证数据**

```sql
-- 确认有至少 2 条打卡记录
SELECT cr.id, cr.user_id, u.real_name, cr.rating, 
       LEFT(cr.reflection, 30) as reflection_preview,
       LEFT(cr.problem, 30) as problem_preview
FROM act_checkin_record cr
JOIN sys_user u ON cr.user_id = u.id
WHERE cr.activity_id = 4 AND cr.deleted = 0;
```

预期结果：至少 2 条记录，评分分别为 5 和 4。

### 1.4 为知识库创建测试文档

创建一个测试用的社团章程文件：

```bash
# 在 ai-service/data/uploads 目录创建测试文件
mkdir -p ai-service/data/uploads
```

文件内容 `ai-service/data/uploads/test_charter.txt`：

```
TestClub2 社团章程

第一章 总则

第一条 本社团名称为 TestClub2，是一个以技术交流为核心的学术性社团。

第二条 本社团宗旨是促进成员之间的技术交流，提升实践能力，组织各类技术分享活动。

第三条 本社团的主要活动包括：
1. 每周举办一次技术分享会
2. 每月组织一次实战 Workshop
3. 每学期举办一次编程竞赛
4. 参与校内外技术交流活动

第二章 成员管理

第四条 入社条件：
1. 对本社团活动有浓厚兴趣
2. 愿意遵守社团章程和规章制度
3. 经社团管理层审核通过

第五条 成员权利：
1. 参加社团组织的各项活动
2. 对社团工作提出建议和意见
3. 享有社团资源的使用权

第六条 成员义务：
1. 按时参加社团活动
2. 积极分享技术心得和经验
3. 维护社团声誉和形象

第三章 活动管理

第七条 活动策划：
1. 活动需提前两周提交策划方案
2. 策划方案需包含活动目标、预算、场地需求
3. 大型活动需经社团联合会审批

第八条 活动执行：
1. 活动负责人需提前确认场地和设备
2. 活动过程中需安排签到和拍照记录
3. 活动结束后需提交活动总结报告

FAQ

Q1、社团活动的频率是怎样的？
A1：每周一次技术分享，每月一次 Workshop，每学期一次竞赛。

Q2、如何加入社团？
A2：在每学期初的招新期间提交申请，经管理层审核后即可加入。

Q3、社团活动需要缴费吗？
A3：社团活动免费，经费由社团联合会拨款和赞助支持。

Q4、活动中遇到问题怎么办？
A4：可以在活动中向负责人反馈，或在打卡时提交问题和建议。
```

### 1.5 插入知识库文档元数据（可选，用于测试列表显示）

```sql
-- 如果需要测试知识库列表，可以手动插入一条记录
INSERT INTO ai_knowledge_document 
(club_id, doc_type, title, source_type, file_path, chunk_count, status, created_at)
VALUES (1, 'charter', 'TestClub2 社团章程', 'upload', 
        'data/uploads/test_charter.txt', 0, 1, NOW());
```

---

## 第二部分：Python AI 服务启动

### 2.1 环境准备

```bash
# 进入 ai-service 目录
cd ai-service

# 检查 Python 版本（需要 3.9+）
python --version

# 安装依赖
pip install -r requirements.txt
```

**依赖说明：**
- `fastapi` + `uvicorn`：Web 框架
- `chromadb`：向量数据库
- `sentence-transformers`：Embedding 模型（bge-large-zh-v1.5）
- `openai`：LLM 调用（MiMo/DeepSeek）
- `pymysql`：MySQL 连接
- `redis`：对话历史存储
- `python-docx`：Word 文档生成
- `rank-bm25`：BM25 检索
- `langchain-text-splitters`：文档切分

### 2.2 检查配置文件

确认 `ai-service/config.py` 中的配置正确：

```python
# LLM 配置
LLM_API_KEY = "你的 API Key"  # 确认有效
LLM_BASE_URL = "https://api.xiaomimimo.com/v1"  # 或其他
LLM_MODEL = "mimo-v2.5"

# MySQL 配置（需与 Java 后端一致）
MYSQL_HOST = "localhost"
MYSQL_PORT = 3306
MYSQL_USER = "root"
MYSQL_PASSWORD = "root"  # 确认密码
MYSQL_DATABASE = "club_management"

# Redis 配置
REDIS_URL = "redis://localhost:6379/1"

# Embedding 模型（首次启动会下载约 1.2GB）
EMBEDDING_MODEL = "BAAI/bge-large-zh-v1.5"
EMBEDDING_DEVICE = "cpu"  # 或 "cuda" 如果有 GPU
```

### 2.3 确认 Redis 运行

```bash
# 检查 Redis 服务状态
Get-Service -Name "Redis*" -ErrorAction SilentlyContinue

# 如果未运行，启动 Redis
# 方式 1：Windows 服务
Start-Service Redis

# 方式 2：直接运行（如果已安装）
redis-server
```

### 2.4 启动 Python 服务

```bash
cd ai-service
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

**预期启动日志：**
```
INFO:     Uvicorn running on http://0.0.0.0:8000
INFO:     AI Service started
```

**首次启动注意：**
- Embedding 模型首次加载需要下载（约 1-3 分钟）
- ChromaDB 会自动创建数据目录

### 2.5 验证 Python 服务

```bash
# 健康检查
curl http://localhost:8000/health

# 预期响应：{"status":"ok"}
```

---

## 第三部分：全链路验证

### 3.1 验证一：生成汇编文档

**前置条件：** 测试数据已生成，打卡截止时间已过。

**操作步骤：**

1. 打开浏览器访问 `http://localhost:5173/checkin/manage/4`
2. 点击"生成汇编文档"按钮
3. 观察后端日志，确认 SQL 查询正常

**预期结果：**
- 前端显示"汇编文档生成成功"
- 页面刷新后汇编状态变为"已生成"
- 点击"查看汇编文档"可看到完整内容

**后端日志应包含：**
```
SELECT ... FROM act_checkin_task WHERE activity_id = 4
SELECT ... FROM act_checkin_record WHERE task_id = ?
SELECT ... FROM act_activity WHERE id = 4
INSERT INTO act_checkin_compilation ...
```

**如果失败：**
- 检查打卡截止时间是否已过去（`deadline < NOW()`）
- 检查是否有打卡记录（`SELECT COUNT(*) FROM act_checkin_record WHERE activity_id = 4`）
- 检查汇编是否已存在（`SELECT * FROM act_checkin_compilation WHERE activity_id = 4`）

### 3.2 验证二：知识库文档上传

**前置条件：** Python 服务已启动。

**操作步骤：**

1. 打开浏览器访问 `http://localhost:5173/ai/knowledge`
2. 点击"上传文档"按钮
3. 填写标题"TestClub2 社团章程"，类型选择"社团章程"
4. 选择 `ai-service/data/uploads/test_charter.txt` 文件
5. 点击"上传"

**预期结果：**
- 前端显示"上传成功"
- 文档列表中出现新记录
- 分块数显示为 >0

**后端日志应包含：**
```
# Java 端
POST /api/v1/ai/knowledge/documents
文件上传成功: /path/to/file

# Python 端
POST /knowledge/documents/ingest
解析文档: test_charter.txt
入库 ChromaDB: club_1, chunk_count=N
INSERT INTO ai_knowledge_document ...
```

**如果失败：**
- 检查 Java→Python 通信：`curl http://localhost:8000/health`
- 检查 Python 日志是否有报错
- 检查文件路径是否正确

### 3.3 验证三：AI 对话（需要 LLM API 可用）

**前置条件：** Python 服务已启动，LLM API Key 有效。

**操作步骤：**

1. 打开浏览器访问 `http://localhost:5173/ai/chat`
2. 在输入框输入"社团活动频率是怎样的？"
3. 点击"发送"

**预期结果：**
- 显示"正在思考..."加载状态
- 几秒后显示 AI 回答，内容应包含"每周一次技术分享，每月一次 Workshop"
- 如果有参考来源，显示来源列表

**后端日志应包含：**
```
# Python 端
POST /chat
RAG 检索: club_1, query="社团活动频率是怎样的？"
向量检索: N 条结果
BM25 检索: M 条结果
RRF 融合: K 条结果
LLM 生成回答: ...
```

**如果失败：**
- 检查 LLM API Key 是否有效
- 检查 ChromaDB 是否有数据（需要先上传知识库文档）
- 如果知识库为空，AI 会回复"知识库中暂无相关内容"

### 3.4 验证四：成果报告生成

**前置条件：** 汇编文档已生成（验证一完成）。

**操作步骤：**

1. 打开浏览器访问 `http://localhost:5173/ai/report/4`
2. 页面应显示"暂无报告"
3. 需要通过活动详情页触发报告生成

**触发方式（通过 API 测试）：**

```bash
# 获取 token
$loginResp = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $loginResp.data.token
$headers = @{Authorization="Bearer $token"}

# 生成报告
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/ai/report/generate" -Method POST -ContentType "application/json" -Headers $headers -Body '{"activityId":4}'
```

**预期结果：**
- API 返回报告草稿（status=DRAFT）
- 刷新报告页面可看到报告内容

**后端日志应包含：**
```
# Java 端
POST /api/v1/ai/report/generate
调用 Python: POST http://localhost:8000/report/generate

# Python 端
POST /report/generate
读取汇编文档: activity_id=4
读取活动信息: Web Workshop
LLM 生成报告: ...
```

**如果失败：**
- 确认汇编文档存在
- 确认 Python 服务可访问
- 检查 LLM API 是否可用

### 3.5 验证五：报告确认入库

**前置条件：** 报告已生成。

**操作步骤：**

1. 刷新报告页面 `http://localhost:5173/ai/report/4`
2. 点击"确认入库"按钮

**预期结果：**
- 显示"报告已确认入库"
- 报告状态变为"已确认"
- Python 日志显示报告已入库 ChromaDB

**后端日志应包含：**
```
# Java 端
POST /api/v1/ai/report/{activityId}/confirm
UPDATE ai_activity_report SET status = 'CONFIRMED'
调用 Python: POST http://localhost:8000/knowledge/ingest-report

# Python 端
POST /knowledge/ingest-report
入库 ChromaDB: club_1, doc_type=activity_report
```

### 3.6 验证六：积分结算

**前置条件：** 打卡已截止。

**操作步骤：**

通过 API 触发积分结算：

```bash
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/checkin/settle/4" -Method POST -Headers $headers
```

**预期结果：**
- 返回结算结果（settledCount, totalPoints）
- 积分流水表新增记录

**验证 SQL：**
```sql
SELECT * FROM member_points_log 
WHERE related_activity_id = 4 
ORDER BY created_at DESC;
```

---

## 第四部分：常见问题排查

### 问题 1：Python 服务启动失败

**现象：** `ModuleNotFoundError` 或 `ImportError`

**解决：**
```bash
pip install -r requirements.txt --upgrade
```

### 问题 2：Embedding 模型下载失败

**现象：** 启动时卡在 "Loading embedding model" 或下载超时

**解决：**
- 使用国内镜像：在 `config.py` 中添加环境变量
- 或手动下载模型到 `~/.cache/huggingface/` 目录

### 问题 3：ChromaDB 连接失败

**现象：** `ConnectionRefusedError` 或 `database is locked`

**解决：**
- 删除 `ai-service/data/chroma/` 目录重建
- 确认没有其他进程占用 ChromaDB

### 问题 4：Java→Python 通信失败

**现象：** `ResourceAccessException` 或 503 错误

**解决：**
- 确认 Python 服务运行在 8000 端口
- 确认 `aiConfig.getBaseUrl()` 返回 `http://localhost:8000`
- 检查 `application.yml` 中的 `ai.base-url` 配置

### 问题 5：LLM API 调用失败

**现象：** `AuthenticationError` 或 `RateLimitError`

**解决：**
- 确认 API Key 有效且未过期
- 检查 API 余额是否充足
- 确认模型名称正确

### 问题 6：汇编生成报"打卡尚未截止"

**解决：**
```sql
-- 强制修改截止时间为过去
UPDATE act_checkin_task 
SET deadline = DATE_SUB(NOW(), INTERVAL 1 HOUR), status = 2
WHERE activity_id = 4;
```

### 问题 7：知识库上传后列表为空

**排查：**
```sql
-- 检查 MySQL 是否有记录
SELECT * FROM ai_knowledge_document WHERE club_id = 1 AND deleted = 0;

-- 如果无记录，检查 Python 日志
-- 如果有记录，检查前端 API 调用是否成功
```

---

## 验证清单

执行完毕后，确认以下项目：

- [ ] AI 新表已创建（8 张）
- [ ] 测试数据已插入（打卡记录 ≥2 条）
- [ ] Python 服务启动成功（`/health` 返回 ok）
- [ ] Redis 服务运行中
- [ ] 汇编文档生成成功
- [ ] 知识库文档上传成功
- [ ] AI 对话可正常回答（或正确提示"暂无相关内容"）
- [ ] 成果报告生成成功
- [ ] 报告确认入库成功
- [ ] 积分结算成功（流水表有记录）

---

## 快速测试命令汇总

```powershell
# 1. 检查 MySQL 表
mysql -u root -proot club_management -e "SHOW TABLES LIKE 'act_checkin%'; SHOW TABLES LIKE 'ai_%';"

# 2. 调整打卡截止时间
mysql -u root -proot club_management -e "UPDATE act_checkin_task SET deadline = DATE_SUB(NOW(), INTERVAL 1 HOUR), status = 2 WHERE activity_id = 4;"

# 3. 启动 Python 服务（后台）
cd c:\Users\lrs\Desktop\gie\ai-service; python -m uvicorn main:app --host 0.0.0.0 --port 8000

# 4. 健康检查
Invoke-RestMethod -Uri "http://localhost:8000/health"

# 5. 登录获取 token
$loginResp = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $loginResp.data.token
$headers = @{Authorization="Bearer $token"}

# 6. 生成汇编
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/checkin/compilation/generate/4" -Method POST -Headers $headers

# 7. 积分结算
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/checkin/settle/4" -Method POST -Headers $headers

# 8. 生成报告
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/ai/report/generate" -Method POST -ContentType "application/json" -Headers $headers -Body '{"activityId":4}'
```
